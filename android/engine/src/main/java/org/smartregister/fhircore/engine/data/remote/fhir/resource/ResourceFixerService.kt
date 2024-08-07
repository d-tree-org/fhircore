/*
 * Copyright 2021 Ona Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.smartregister.fhircore.engine.data.remote.fhir.resource

import ca.uhn.fhir.rest.client.api.IGenericClient
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.extensions.logicalId
import com.google.android.fhir.get
import java.util.Date
import javax.inject.Inject
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CarePlan
import org.hl7.fhir.r4.model.CarePlan.CarePlanActivityComponent
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Meta
import org.hl7.fhir.r4.model.Period
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.Task
import org.smartregister.fhircore.engine.util.SystemConstants.CARE_PLAN_REFERENCE_SYSTEM
import org.smartregister.fhircore.engine.util.SystemConstants.QUESTIONNAIRE_REFERENCE_SYSTEM
import org.smartregister.fhircore.engine.util.SystemConstants.RESOURCE_CREATED_ON_TAG_SYSTEM
import org.smartregister.fhircore.engine.util.extension.extractId
import org.smartregister.fhircore.engine.util.extension.generateCreatedOn
import org.smartregister.fhircore.engine.util.extension.getResourcesByIds
import org.smartregister.fhircore.engine.util.extension.shouldShowOnProfile
import org.smartregister.fhircore.engine.util.extension.toTaskStatus
import timber.log.Timber

class ResourceFixerService
@Inject
constructor(
  private val fhirClient: IGenericClient,
  private val fhirEngine: FhirEngine,
) {
  suspend fun fixCurrentCarePlan(patientId: String, carePlanId: String) {
    val carePlan: CarePlan = fhirEngine.get(carePlanId)
    val tasks = getMissingTasks(carePlan)
    if (tasks.isEmpty()) {
      return
    }
    Timber.i("Found missing tasks: ${tasks.size}")
    handleMissingTasks(patientId, carePlan, tasks)
    return
  }

  private suspend fun getMissingTasks(carePlan: CarePlan): List<CarePlanActivityComponent> {
    val activityOnList = mutableMapOf<String, CarePlanActivityComponent>()
    val missingTasks = mutableListOf<CarePlanActivityComponent>()
    val tasksToFetch =
      carePlan.activity.mapNotNull { planActivity ->
        if (planActivity.shouldShowOnProfile()) {
          planActivity.outcomeReference.firstOrNull()?.extractId()?.also { taskId ->
            activityOnList[taskId] = planActivity
          }
        } else {
          null
        }
      }
    val tasks = fhirEngine.getResourcesByIds<Task>(tasksToFetch).associateBy { it.logicalId }
    activityOnList.forEach { (taskId, activity) ->
      if (activity.detail?.status != CarePlan.CarePlanActivityStatus.SCHEDULED) {
        if (!tasks.containsKey(taskId)) {
          missingTasks.add(activity)
        }
      }
    }
    return missingTasks
  }

  private suspend fun handleMissingTasks(
    patientId: String,
    carePlan: CarePlan,
    tasks: List<CarePlanActivityComponent>,
  ) {
    val bundle = Bundle()
    bundle.setType(Bundle.BundleType.TRANSACTION)
    bundle.entry.addAll(
      tasks.map { task ->
        val taskId = task.outcomeReference.firstOrNull()?.extractId()
        Bundle.BundleEntryComponent().apply {
          request =
            Bundle.BundleEntryRequestComponent().apply {
              method = Bundle.HTTPVerb.GET
              url = "${ResourceType.Task.name}/$taskId"
            }
        }
      },
    )

    val tasksToRecreate = mutableListOf<CarePlanActivityComponent>()
    val existingTasks = mutableListOf<Task>()

    val resBundle = fhirClient.transaction().withBundle(bundle).execute()
    for ((index, entry) in resBundle.entry.withIndex()) {
      if (entry.hasResource()) {
        existingTasks.add(entry.resource as Task)
      } else {
        tasksToRecreate.add(tasks[index])
      }
    }

    val resourceToSave = mutableListOf<Resource>()
    resourceToSave.addAll(existingTasks)

    if (tasksToRecreate.isNotEmpty()) {
      resourceToSave.addAll(
        tasksToRecreate.map {
          createGenericTask(
            patientId = patientId,
            activity = it,
            carePlan = carePlan,
          )
        },
      )
    }

    Timber.e("Task on server: ${existingTasks.size}, Tasks to recreate: ${tasksToRecreate.size}")
    fhirEngine.create(*resourceToSave.toTypedArray())
  }

  private fun createGenericTask(
    patientId: String,
    activity: CarePlanActivityComponent,
    carePlan: CarePlan,
  ): Task {
    val questId = activity.detail.code.coding.firstOrNull()?.code
    val patientReference = Reference().apply { reference = "Patient/$patientId" }
    val questRef = Reference().apply { reference = questId }

    val period =
      Period().apply {
        start = Date()
        end = Date()
      }

    val task =
      Task().apply {
        id = activity.outcomeReference.firstOrNull()?.extractId()
        status = activity.detail.status.toTaskStatus()
        intent = Task.TaskIntent.PLAN
        priority = Task.TaskPriority.ROUTINE
        description = activity.detail.description
        authoredOn = Date()
        lastModified = Date()
        `for` = patientReference
        executionPeriod = period
        requester = carePlan.author
        owner = carePlan.author
        reasonReference = questRef
      }

    val meta =
      Meta().apply {
        tag =
          carePlan.meta.tag
            .filter {
              it.system != RESOURCE_CREATED_ON_TAG_SYSTEM && it.system != CARE_PLAN_REFERENCE_SYSTEM
            }
            .toMutableList()
        tag.add(
          Coding().apply {
            system = CARE_PLAN_REFERENCE_SYSTEM
            code = "CarePlan/${carePlan.id}"
            display = carePlan.title
          },
        )
        tag.add(
          Coding().apply {
            system = QUESTIONNAIRE_REFERENCE_SYSTEM
            code = questId
          },
        )
      }

    task.meta = meta
    task.generateCreatedOn()
    return task
  }
}
