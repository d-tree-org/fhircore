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

package org.smartregister.fhircore.quest.ui.shared.models

import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CarePlan
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Practitioner
import org.hl7.fhir.r4.model.RelatedPerson
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.Task
import org.smartregister.fhircore.engine.data.domain.Guardian
import org.smartregister.fhircore.engine.data.domain.PhoneContact
import org.smartregister.fhircore.engine.domain.model.FormButtonData
import org.smartregister.fhircore.engine.domain.model.TracingAttempt
import org.smartregister.fhircore.quest.ui.family.profile.model.FamilyMemberViewState

sealed class ProfileViewData(
  open val logicalId: String = "",
  open val name: String = "",
  open val identifier: String? = null,
) {
  data class PatientProfileViewData(
    override val logicalId: String = "",
    override val name: String = "",
    override val identifier: String? = null,
    val givenName: String = "",
    val familyName: String = "",
    val status: String? = null,
    val sex: String = "",
    val age: String = "",
    val dob: String = "",
    val showListsHighlights: Boolean = true,
    val hasMissingTasks: Boolean = false,
    val tasks: List<PatientProfileRowItem> = emptyList(),
    val forms: List<FormButtonData> = emptyList(),
    val medicalHistoryData: List<PatientProfileRowItem> = emptyList(),
    val upcomingServices: List<PatientProfileRowItem> = emptyList(),
    val ancCardData: List<PatientProfileRowItem> = emptyList(),
    val address: String = "",
    val identifierKey: String = "",
    val showIdentifierInProfile: Boolean = false,
    val conditions: List<Condition> = emptyList(),
    val otherPatients: List<Resource> = emptyList(),
    val viewChildText: String = "",
    val guardians: List<Guardian> = emptyList(),
    val tracingTask: Task = Task(),
    val addressDistrict: String = "",
    val addressTracingCatchment: String = "",
    val addressPhysicalLocator: String = "",
    val currentCarePlan: CarePlan? = null,
    val visitNumber: String? = null,
    val phoneContacts: List<String> = emptyList(),
    val observations: List<Observation> = emptyList(),
    val practitioners: List<Practitioner> = emptyList(),
  ) : ProfileViewData(name = name, logicalId = logicalId, identifier = identifier) {
    val tasksCompleted =
      currentCarePlan != null &&
        tasks.isNotEmpty() &&
        tasks.all { it.subtitleStatus == CarePlan.CarePlanActivityStatus.COMPLETED.name }

    private val guardiansRelatedPersonResource = guardians.filterIsInstance<RelatedPerson>()

    val populationResources: ArrayList<Resource> by lazy {
      val resources = conditions + guardiansRelatedPersonResource + observations
      val resourcesAsBundle = Bundle().apply { resources.map { this.addEntry().resource = it } }
      val list = arrayListOf(*practitioners.toTypedArray(), resourcesAsBundle)
      if (currentCarePlan != null) {
        list.add(currentCarePlan)
      }
      list
    }
  }

  data class FamilyProfileViewData(
    override val logicalId: String = "",
    override val name: String = "",
    val address: String = "",
    val age: String = "",
    val familyMemberViewStates: List<FamilyMemberViewState> = emptyList(),
  ) : ProfileViewData(logicalId = logicalId, name = name)

  data class TracingProfileData(
    override val logicalId: String = "",
    override val name: String = "",
    val isHomeTracing: Boolean? = null,
    val sex: String = "",
    val age: String = "",
    val dueDate: String? = null,
    override val identifier: String? = null,
    val identifierKey: String = "",
    val currentAttempt: TracingAttempt? = null,
    val showIdentifierInProfile: Boolean = false,
    val addressDistrict: String = "",
    val addressTracingCatchment: String = "",
    val addressPhysicalLocator: String = "",
    val phoneContacts: List<PhoneContact> = emptyList(),
    val tracingTasks: List<Task> = emptyList(),
    val carePlans: List<CarePlan> = emptyList(),
    val guardians: List<Guardian> = emptyList(),
    val practitioners: List<Practitioner> = emptyList(),
    val conditions: List<Condition> = emptyList(),
  ) : ProfileViewData(logicalId = logicalId, name = name) {
    val guardiansRelatedPersonResource =
      guardians.filterIsInstance<RelatedPerson>().filter { it.telecomFirstRep.hasValue() }
    val populationResources: ArrayList<Resource> by lazy {
      val resources = conditions + guardiansRelatedPersonResource
      val resourcesAsBundle = Bundle().apply { resources.map { this.addEntry().resource = it } }
      arrayListOf(*carePlans.toTypedArray(), *practitioners.toTypedArray(), resourcesAsBundle)
    }
    val hasFinishedAttempts: Boolean =
      currentAttempt.run {
        isHomeTracing?.let {
          val maxAttempts = if (it) 2 else 3
          if (this != null) {
            return@run this.numberOfAttempts >= maxAttempts
          }
        }
        return@run true
      }
  }
}
