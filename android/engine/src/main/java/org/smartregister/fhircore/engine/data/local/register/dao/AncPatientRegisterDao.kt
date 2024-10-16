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

package org.smartregister.fhircore.engine.data.local.register.dao

import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.extensions.logicalId
import com.google.android.fhir.get
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.search
import javax.inject.Inject
import javax.inject.Singleton
import org.hl7.fhir.r4.model.CarePlan
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Flag
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Task
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.data.local.DefaultRepository
import org.smartregister.fhircore.engine.domain.model.ProfileData
import org.smartregister.fhircore.engine.domain.model.RegisterData
import org.smartregister.fhircore.engine.domain.model.VisitStatus
import org.smartregister.fhircore.engine.domain.repository.RegisterDao
import org.smartregister.fhircore.engine.domain.util.PaginationConstant
import org.smartregister.fhircore.engine.util.DefaultDispatcherProvider
import org.smartregister.fhircore.engine.util.extension.extractAddress
import org.smartregister.fhircore.engine.util.extension.extractId
import org.smartregister.fhircore.engine.util.extension.extractName
import org.smartregister.fhircore.engine.util.extension.extractOfficialIdentifier
import org.smartregister.fhircore.engine.util.extension.milestonesDue
import org.smartregister.fhircore.engine.util.extension.milestonesOverdue
import org.smartregister.fhircore.engine.util.extension.toAgeDisplay

@Singleton
class AncPatientRegisterDao
@Inject
constructor(
  val fhirEngine: FhirEngine,
  val defaultRepository: DefaultRepository,
  val configurationRegistry: ConfigurationRegistry,
  val dispatcherProvider: DefaultDispatcherProvider,
) : RegisterDao {

  override suspend fun loadRegisterData(
    currentPage: Int,
    loadAll: Boolean,
    appFeatureName: String?,
    patientSearchText: String?,
  ): List<RegisterData> {
    val pregnancies =
      fhirEngine
        .search<Condition> {
          //         [].forEach { filterBy(it) }
          sort(Patient.NAME, Order.ASCENDING)
          if (!loadAll) {
            count = PaginationConstant.DEFAULT_PAGE_SIZE + PaginationConstant.EXTRA_ITEM_COUNT
          }
          from = currentPage * PaginationConstant.DEFAULT_PAGE_SIZE
        }
        .map { it.resource }
        .distinctBy { it.subject.reference }

    val patients =
      pregnancies
        .map { fhirEngine.get<Patient>(it.subject.extractId()) }
        .sortedBy { it.nameFirstRep.family }

    return patients.map { patient ->
      val carePlans =
        defaultRepository.searchResourceFor<CarePlan>(
          subjectId = patient.logicalId,
          subjectParam = CarePlan.SUBJECT,
        )

      RegisterData.AncRegisterData(
        logicalId = patient.logicalId,
        name = patient.extractName(),
        identifier = patient.extractOfficialIdentifier(),
        gender = patient.gender,
        age = patient.birthDate.toAgeDisplay(),
        address = patient.extractAddress(),
        visitStatus = getVisitStatus(carePlans),
        servicesDue = carePlans.sumOf { it.milestonesDue().size },
        servicesOverdue = carePlans.sumOf { it.milestonesOverdue().size },
        familyName = if (patient.hasName()) patient.nameFirstRep.family else null,
      )
    }
  }

  override suspend fun loadProfileData(appFeatureName: String?, resourceId: String): ProfileData? {
    val patient = defaultRepository.loadResource<Patient>(resourceId)!!
    val carePlans =
      defaultRepository.searchResourceFor<CarePlan>(
        subjectId = patient.logicalId,
        subjectParam = CarePlan.SUBJECT,
      )

    return ProfileData.AncProfileData(
      logicalId = patient.logicalId,
      birthdate = patient.birthDate,
      name = patient.extractName(),
      identifier = patient.extractOfficialIdentifier(),
      gender = patient.gender,
      age = patient.birthDate.toAgeDisplay(),
      address = patient.extractAddress(),
      visitStatus = getVisitStatus(carePlans),
      services = carePlans,
      tasks =
        defaultRepository.searchResourceFor(
          subjectId = patient.logicalId,
          subjectParam = Task.SUBJECT,
        ),
      conditions =
        defaultRepository.searchResourceFor(
          subjectId = patient.logicalId,
          subjectParam = Condition.SUBJECT,
        ),
      flags =
        defaultRepository.searchResourceFor(
          subjectId = patient.logicalId,
          subjectParam = Flag.SUBJECT,
        ),
      visits =
        defaultRepository.searchResourceFor(
          subjectId = patient.logicalId,
          subjectParam = Encounter.SUBJECT,
        ),
    )
  }

  //    fhirEngine.count<Condition> { getRegisterDataFilters().forEach { filterBy(it) } }

  private fun getVisitStatus(carePlans: List<CarePlan>): VisitStatus {
    var visitStatus = VisitStatus.PLANNED
    if (carePlans.any { it.milestonesOverdue().isNotEmpty() }) {
      visitStatus = VisitStatus.OVERDUE
    } else if (carePlans.any { it.milestonesDue().isNotEmpty() }) visitStatus = VisitStatus.DUE

    return visitStatus
  }
}
