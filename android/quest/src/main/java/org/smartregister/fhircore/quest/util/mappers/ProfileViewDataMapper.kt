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

package org.smartregister.fhircore.quest.util.mappers

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Color
import com.google.android.fhir.datacapture.extensions.logicalId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import org.hl7.fhir.r4.model.CarePlan
import org.hl7.fhir.r4.model.Task
import org.smartregister.fhircore.engine.domain.model.HealthStatus
import org.smartregister.fhircore.engine.domain.model.ProfileData
import org.smartregister.fhircore.engine.domain.util.DataMapper
import org.smartregister.fhircore.engine.ui.theme.DefaultColor
import org.smartregister.fhircore.engine.ui.theme.InfoColor
import org.smartregister.fhircore.engine.ui.theme.OverdueColor
import org.smartregister.fhircore.engine.ui.theme.SuccessColor
import org.smartregister.fhircore.engine.util.extension.asDdMmYyyy
import org.smartregister.fhircore.engine.util.extension.canBeCompleted
import org.smartregister.fhircore.engine.util.extension.extractId
import org.smartregister.fhircore.engine.util.extension.extractVisitNumber
import org.smartregister.fhircore.engine.util.extension.getQuestionnaire
import org.smartregister.fhircore.engine.util.extension.getQuestionnaireName
import org.smartregister.fhircore.engine.util.extension.makeItReadable
import org.smartregister.fhircore.engine.util.extension.translateGender
import org.smartregister.fhircore.quest.R
import org.smartregister.fhircore.quest.ui.family.profile.model.FamilyMemberTask
import org.smartregister.fhircore.quest.ui.family.profile.model.FamilyMemberViewState
import org.smartregister.fhircore.quest.ui.shared.models.PatientProfileRowItem
import org.smartregister.fhircore.quest.ui.shared.models.PatientProfileViewSection
import org.smartregister.fhircore.quest.ui.shared.models.ProfileViewData
import org.smartregister.fhircore.quest.util.extensions.isHomeTracingTask

class ProfileViewDataMapper @Inject constructor(@ApplicationContext val context: Context) :
  DataMapper<ProfileData, ProfileViewData> {

  private val simpleDateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

  override fun transformInputToOutputModel(inputModel: ProfileData): ProfileViewData {
    return when (inputModel) {
      is ProfileData.AncProfileData ->
        ProfileViewData.PatientProfileViewData(
          logicalId = inputModel.logicalId,
          name = inputModel.name,
          sex = inputModel.gender.translateGender(context),
          age = inputModel.age,
          dob = inputModel.birthdate.formatDob(),
          identifier = inputModel.identifier,
        )
      is ProfileData.HivProfileData ->
        ProfileViewData.PatientProfileViewData(
          logicalId = inputModel.logicalId,
          name = inputModel.name,
          givenName = inputModel.givenName,
          familyName = inputModel.familyName,
          sex = inputModel.gender.translateGender(context),
          age = inputModel.age,
          dob = inputModel.birthdate.formatDob(),
          identifier = inputModel.identifier,
          address = inputModel.address,
          addressDistrict = inputModel.addressDistrict,
          addressTracingCatchment = inputModel.addressTracingCatchment,
          addressPhysicalLocator = inputModel.addressPhysicalLocator,
          phoneContacts = inputModel.phoneContacts,
          identifierKey = inputModel.healthStatus.retrieveDisplayIdentifierKey(),
          showIdentifierInProfile = inputModel.showIdentifierInProfile,
          showListsHighlights = false,
          conditions = inputModel.conditions,
          otherPatients = inputModel.otherPatients,
          viewChildText =
            context.getString(R.string.view_children_x, inputModel.otherPatients.size.toString()),
          observations = inputModel.observations,
          guardians = inputModel.guardians,
          currentCarePlan = inputModel.currentCarePlan,
          visitNumber = inputModel.currentCarePlan?.extractVisitNumber(),
          hasMissingTasks = inputModel.hasMissingTasks,
          tasks =
            inputModel.tasks.map {
              PatientProfileRowItem(
                id = it.task.outcomeReference.first().extractId(),
                actionFormId = if (it.task.canBeCompleted()) it.task.getQuestionnaire() else null,
                title = "", // it.description,
                subtitle = "", // context.getString(R.string.due_on,
                // it.executionPeriod.start.makeItReadable()),
                taskExists = it.taskExists,
                profileViewSection = PatientProfileViewSection.TASKS,
                actionButtonIcon =
                  if (it.task.detail.status == CarePlan.CarePlanActivityStatus.COMPLETED) {
                    Icons.Filled.Check
                  } else Icons.Filled.Add,
                actionIconColor =
                  if (it.task.detail.status == CarePlan.CarePlanActivityStatus.COMPLETED) {
                    SuccessColor
                  } else it.task.detail.status.retrieveColorCode(),
                actionButtonColor = it.task.detail.status.retrieveColorCode(),
                actionButtonText = it.task.getQuestionnaireName(),
                subtitleStatus = it.task.detail.status.name,
              )
            },
          practitioners = inputModel.practitioners,
        )
      is ProfileData.DefaultProfileData ->
        ProfileViewData.PatientProfileViewData(
          logicalId = inputModel.logicalId,
          name = inputModel.name,
          identifier = inputModel.identifier,
          age = inputModel.age,
          sex = inputModel.gender.translateGender(context),
          dob = inputModel.birthdate.formatDob(),
          tasks =
            inputModel.tasks.take(DEFAULT_TASKS_COUNT).map {
              PatientProfileRowItem(
                id = it.logicalId,
                actionFormId =
                  if (it.status == Task.TaskStatus.READY && it.hasReasonReference()) {
                    it.reasonReference.extractId()
                  } else {
                    null
                  },
                title = it.description,
                subtitle =
                  context.getString(R.string.due_on, it.executionPeriod.start.makeItReadable()),
                profileViewSection = PatientProfileViewSection.TASKS,
                actionButtonIcon =
                  if (it.status == Task.TaskStatus.COMPLETED) {
                    Icons.Filled.Check
                  } else Icons.Filled.Add,
                actionIconColor =
                  if (it.status == Task.TaskStatus.COMPLETED) {
                    SuccessColor
                  } else it.status.retrieveColorCode(),
                actionButtonColor = it.status.retrieveColorCode(),
                actionButtonText = it.description,
              )
            },
        )
      is ProfileData.FamilyProfileData ->
        ProfileViewData.FamilyProfileViewData(
          logicalId = inputModel.logicalId,
          name = context.getString(R.string.family_suffix, inputModel.name),
          address = inputModel.address,
          age = inputModel.age,
          familyMemberViewStates =
            inputModel.members.map { memberProfileData ->
              FamilyMemberViewState(
                patientId = memberProfileData.id,
                birthDate = memberProfileData.birthdate,
                age = memberProfileData.age,
                gender = memberProfileData.gender.translateGender(context),
                name = memberProfileData.name,
                memberTasks =
                  memberProfileData.tasks
                    .filter { it.status == Task.TaskStatus.READY }
                    .take(DEFAULT_TASKS_COUNT)
                    .map {
                      FamilyMemberTask(
                        taskId = it.logicalId,
                        task = it.description,
                        taskStatus = it.status,
                        colorCode = it.status.retrieveColorCode(),
                        taskFormId =
                          if (it.status == Task.TaskStatus.READY && it.hasReasonReference()) {
                            it.reasonReference.extractId()
                          } else {
                            null
                          },
                      )
                    },
              )
            },
        )
      is ProfileData.TracingProfileData ->
        ProfileViewData.TracingProfileData(
          logicalId = inputModel.logicalId,
          name = inputModel.name,
          sex = inputModel.gender.translateGender(context),
          age = inputModel.age,
          isHomeTracing = inputModel.tasks.firstOrNull { x -> x.isHomeTracingTask() } != null,
          currentAttempt = inputModel.currentAttempt,
          dueDate = inputModel.dueDate?.asDdMmYyyy(),
          identifierKey = inputModel.healthStatus.retrieveDisplayIdentifierKey(),
          identifier = inputModel.identifier,
          showIdentifierInProfile = true,
          phoneContacts = inputModel.phoneContacts,
          addressDistrict = inputModel.addressDistrict,
          addressTracingCatchment = inputModel.addressTracingCatchment,
          addressPhysicalLocator = inputModel.addressPhysicalLocator,
          carePlans = inputModel.services,
          guardians = inputModel.guardians,
          practitioners = inputModel.practitioners,
          conditions = inputModel.conditions,
          tracingTasks = inputModel.tasks,
        )
    }
  }

  private fun Task.TaskStatus.retrieveColorCode(): Color =
    when (this) {
      Task.TaskStatus.READY -> InfoColor
      Task.TaskStatus.CANCELLED -> OverdueColor
      Task.TaskStatus.FAILED -> OverdueColor
      Task.TaskStatus.COMPLETED -> DefaultColor
      else -> DefaultColor
    }

  private fun CarePlan.CarePlanActivityStatus.retrieveColorCode(): Color =
    when (this) {
      CarePlan.CarePlanActivityStatus.NOTSTARTED -> InfoColor
      CarePlan.CarePlanActivityStatus.CANCELLED -> OverdueColor
      CarePlan.CarePlanActivityStatus.STOPPED -> OverdueColor
      CarePlan.CarePlanActivityStatus.COMPLETED -> DefaultColor
      else -> DefaultColor
    }

  private fun HealthStatus.retrieveDisplayIdentifierKey(): String =
    when (this) {
      HealthStatus.EXPOSED_INFANT -> "HCC Number"
      HealthStatus.CHILD_CONTACT,
      HealthStatus.SEXUAL_CONTACT,
      HealthStatus.COMMUNITY_POSITIVE, -> "HTS Number"
      else -> "ART Number"
    }

  private fun Date?.formatDob(): String = if (this == null) "" else simpleDateFormat.format(this)

  companion object {
    const val DEFAULT_TASKS_COUNT = 5 // TODO Configure tasks to display
  }
}
