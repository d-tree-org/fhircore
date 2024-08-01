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

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PROTECTED
import ca.uhn.fhir.model.api.TemporalPrecisionEnum
import ca.uhn.fhir.rest.gclient.TokenClientParam
import ca.uhn.fhir.rest.param.ParamPrefixEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.extensions.logicalId
import com.google.android.fhir.search.BaseSearch
import com.google.android.fhir.search.Operation
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.Search
import com.google.android.fhir.search.StringFilterModifier
import com.google.android.fhir.search.filter.TokenParamFilterCriterion
import com.google.android.fhir.search.has
import com.google.android.fhir.search.revInclude
import com.google.android.fhir.search.search
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.math.max
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.hl7.fhir.r4.model.CarePlan
import org.hl7.fhir.r4.model.CodeType
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.DateType
import org.hl7.fhir.r4.model.ListResource
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Practitioner
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.Task
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.configuration.app.ApplicationConfiguration
import org.smartregister.fhircore.engine.data.domain.Guardian
import org.smartregister.fhircore.engine.data.domain.PregnancyStatus
import org.smartregister.fhircore.engine.data.local.DefaultRepository
import org.smartregister.fhircore.engine.data.local.RegisterFilter
import org.smartregister.fhircore.engine.data.local.TracingAgeFilterEnum
import org.smartregister.fhircore.engine.data.local.TracingRegisterFilter
import org.smartregister.fhircore.engine.data.local.tracing.TracingRepository
import org.smartregister.fhircore.engine.domain.model.HealthStatus
import org.smartregister.fhircore.engine.domain.model.ProfileData
import org.smartregister.fhircore.engine.domain.model.RegisterData
import org.smartregister.fhircore.engine.domain.repository.RegisterDao
import org.smartregister.fhircore.engine.domain.util.PaginationConstant
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import org.smartregister.fhircore.engine.util.extension.activePatientConditions
import org.smartregister.fhircore.engine.util.extension.asDdMmmYyyy
import org.smartregister.fhircore.engine.util.extension.asReference
import org.smartregister.fhircore.engine.util.extension.extractAddress
import org.smartregister.fhircore.engine.util.extension.extractAddressDistrict
import org.smartregister.fhircore.engine.util.extension.extractAddressState
import org.smartregister.fhircore.engine.util.extension.extractAddressText
import org.smartregister.fhircore.engine.util.extension.extractFamilyName
import org.smartregister.fhircore.engine.util.extension.extractHealthStatusFromMeta
import org.smartregister.fhircore.engine.util.extension.extractName
import org.smartregister.fhircore.engine.util.extension.extractOfficialIdentifier
import org.smartregister.fhircore.engine.util.extension.extractTelecom
import org.smartregister.fhircore.engine.util.extension.fetch
import org.smartregister.fhircore.engine.util.extension.getPregnancyStatus
import org.smartregister.fhircore.engine.util.extension.referenceValue
import org.smartregister.fhircore.engine.util.extension.safeSubList
import org.smartregister.fhircore.engine.util.extension.toAgeDisplay
import timber.log.Timber

abstract class TracingRegisterDao(
  open val fhirEngine: FhirEngine,
  val defaultRepository: DefaultRepository,
  private val tracingRepository: TracingRepository,
  val configurationRegistry: ConfigurationRegistry,
  val dispatcherProvider: DispatcherProvider,
  val sharedPreferencesHelper: SharedPreferencesHelper,
) : RegisterDao {

  @VisibleForTesting(otherwise = PROTECTED) abstract val tracingCoding: Coding

  private fun hivPatientIdentifier(patient: Patient): String =
    // would either be an ART or HCC number
    patient.extractOfficialIdentifier() ?: HivRegisterDao.ResourceValue.BLANK

  private val currentPractitioner by lazy {
    sharedPreferencesHelper.read(
      key = SharedPreferenceKey.PRACTITIONER_ID.name,
      defaultValue = null,
    )
  }

  private val filtersForValidTask: BaseSearch.() -> Unit = {
    filter(
      TokenClientParam("_tag"),
      { value = of(CodeType(tracingCoding.code)) },
    )
    filter(
      Task.STATUS,
      { value = of(Task.TaskStatus.READY.toCode()) },
      { value = of(Task.TaskStatus.INPROGRESS.toCode()) },
      operation = Operation.OR,
    )
    filter(
      Task.PERIOD,
      {
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
        value =
          of(DateTimeType(Date.from(today.plusDays(1L).toInstant()), TemporalPrecisionEnum.DAY))
        prefix = ParamPrefixEnum.LESSTHAN
      },
    )
  }

  private val filterPatientSearchWithText: Search.(String) -> Unit = { patientSearchText ->
    if (patientSearchText.contains(Regex("[0-9]{2}"))) {
      filter(Patient.IDENTIFIER, { value = of(patientSearchText) })
    } else {
      filter(
        Patient.NAME,
        {
          modifier = StringFilterModifier.CONTAINS
          value = patientSearchText
        },
      )
    }
  }

  private val filterPatientSearchWithHealthStatus: Search.(Iterable<HealthStatus>) -> Unit =
    { healthStatuses ->
      val tagFilterCriteria =
        healthStatuses
          .map { it.name.lowercase() }
          .flatMap { listOf(CodeType(it), CodeType(it.replace("_", "-"))) }
          .map<CodeType, TokenParamFilterCriterion.() -> Unit> {
            return@map { value = of(it) }
          }

      filter(TokenClientParam("_tag"), *tagFilterCriteria.toTypedArray(), operation = Operation.OR)
    }

  private val filterPatientSearchWithAge: Search.(TracingAgeFilterEnum) -> Unit =
    { tracingAgeFilterEnum ->
      val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
      when (tracingAgeFilterEnum) {
        TracingAgeFilterEnum.ZERO_TO_2 -> {
          filter(
            Patient.BIRTHDATE,
            {
              value = of(DateType(Date.from(today.minusYears(2L).toInstant())))
              prefix = ParamPrefixEnum.GREATERTHAN
            },
          )
        }
        TracingAgeFilterEnum.ZERO_TO_18 -> {
          filter(
            Patient.BIRTHDATE,
            {
              value = of(DateType(Date.from(today.minusYears(18L).toInstant())))
              prefix = ParamPrefixEnum.GREATERTHAN
            },
          )
        }
        TracingAgeFilterEnum.PLUS_18 -> {
          filter(
            Patient.BIRTHDATE,
            {
              value = of(DateType(Date.from(today.minusYears(18L).toInstant())))
              prefix = ParamPrefixEnum.LESSTHAN_OR_EQUALS
            },
          )
        }
      }
    }

  private suspend fun searchRegister(
    filters: RegisterFilter,
    loadAll: Boolean,
    page: Int = -1,
    patientSearchText: String? = null,
    includeListResource: Boolean = false,
  ): List<PatientTracingItem> {
    filters as TracingRegisterFilter
    return fhirEngine
      .fetch<Patient>(
        offset = max(page, 0) * PaginationConstant.DEFAULT_PAGE_SIZE,
        loadAll = loadAll,
      ) {
        has<Task>(Task.SUBJECT) { filtersForValidTask() }

        if (!patientSearchText.isNullOrBlank()) {
          filterPatientSearchWithText(patientSearchText)
        }

        filters.patientCategory?.let { filterPatientSearchWithHealthStatus(it) }

        if (filters.isAssignedToMe) {
          currentPractitioner?.let {
            filter(
              Patient.GENERAL_PRACTITIONER,
              { value = it.asReference(ResourceType.Practitioner).reference },
            )
          }
        }

        filters.age?.let { filterPatientSearchWithAge(it) }

        revInclude<Task>(Task.SUBJECT) { filtersForValidTask() }

        if (includeListResource) {
          revInclude<ListResource>(ListResource.SUBJECT) {
            filter(ListResource.STATUS, { value = of(ListResource.ListStatus.CURRENT.toCode()) })
            sort(ListResource.TITLE, Order.DESCENDING)
          }
        }
      }
      .mapNotNull { searchResult ->
        val tasks =
          searchResult.revIncluded
            ?.get(ResourceType.Task to Task.SUBJECT.paramName)
            ?.map { it as Task }
            ?.filter {
              val matchesReasonCode =
                filters.reasonCode != null &&
                  it.reasonCode.coding.any { coding ->
                    coding.code.equals(filters.reasonCode, ignoreCase = true)
                  }
              matchesReasonCode || !it.reasonCode.isEmpty
            } ?: emptyList()
        val listResource =
          searchResult.revIncluded
            ?.get(ResourceType.List to ListResource.SUBJECT.paramName)
            ?.map { it as ListResource }
            ?.maxBy { it.title }

        if (tasks.isEmpty()) {
          null
        } else {
          PatientTracingItem(
            patient = searchResult.resource,
            tracingTasks = tasks,
            tracingListResource = listResource,
          )
        }
      }
  }

  override suspend fun loadRegisterFiltered(
    currentPage: Int,
    loadAll: Boolean,
    filters: RegisterFilter,
    patientSearchText: String?,
  ): List<RegisterData> {
    val patientTracingItems =
      searchRegister(
        filters,
        patientSearchText = patientSearchText,
        includeListResource = true,
        loadAll = true,
      )

    val tracingData: List<RegisterData.TracingRegisterData> =
      patientTracingItems
        .map {
          val patient = it.patient
          patient.toTracingRegisterData(it.tracingTasks, it.tracingListResource)
        }
        .filter { it.reasons.any() }
        .sortedWith(compareBy({ it.attempts }, { it.lastAttemptDate }, { it.firstAdded }))

    return if (!loadAll) {
      val from = currentPage * PaginationConstant.DEFAULT_PAGE_SIZE
      val to = from + PaginationConstant.DEFAULT_PAGE_SIZE + PaginationConstant.EXTRA_ITEM_COUNT
      tracingData.safeSubList(from..to)
    } else {
      tracingData
    }
  }

  override suspend fun loadRegisterData(
    currentPage: Int,
    loadAll: Boolean,
    appFeatureName: String?,
    patientSearchText: String?,
  ): List<RegisterData> {
    val tracingPatients =
      fhirEngine.fetch<Patient> {
        has<Task>(Task.SUBJECT) { filtersForValidTask() }

        if (!patientSearchText.isNullOrBlank()) {
          filterPatientSearchWithText(patientSearchText)
        }
      }

    return tracingPatients
      .map { it.resource }
      .map { it.toTracingRegisterData() }
      .filter { it.reasons.any() }
      .sortedWith(compareBy({ it.attempts }, { it.lastAttemptDate }, { it.firstAdded }))
      .let {
        if (!loadAll) {
          val from = currentPage * PaginationConstant.DEFAULT_PAGE_SIZE
          val to = from + PaginationConstant.DEFAULT_PAGE_SIZE + PaginationConstant.EXTRA_ITEM_COUNT
          it.safeSubList(from..to)
        } else {
          it
        }
      }
  }

  override suspend fun loadProfileData(appFeatureName: String?, resourceId: String): ProfileData? {
    val patient = defaultRepository.loadResource<Patient>(resourceId)
    return patient?.let {
      val metaCodingSystemTag =
        configurationRegistry.getAppConfigs().patientTypeFilterTagViaMetaCodingSystem
      val tasks = validTasks(patient)

      val attempt = tracingRepository.getTracingAttempt(patient, tasks)
      val carePlans = patient.activeCarePlans()

      var dueData = carePlans.firstOrNull()?.period?.end

      if (dueData == null) {
        tasks.minOfOrNull { task -> task.authoredOn }?.let { date -> dueData = date }
      }

      ProfileData.TracingProfileData(
        identifier = hivPatientIdentifier(patient),
        logicalId = patient.logicalId,
        birthdate = patient.birthDate,
        name = patient.extractName(),
        gender = patient.gender,
        age = patient.birthDate.toAgeDisplay(),
        dueDate = dueData,
        address = patient.extractAddress(),
        addressDistrict = patient.extractAddressDistrict(),
        addressTracingCatchment = patient.extractAddressState(),
        addressPhysicalLocator = patient.extractAddressText(),
        phoneContacts = patient.extractTelecom(),
        chwAssigned = patient.generalPractitionerFirstRep,
        showIdentifierInProfile = true,
        healthStatus = patient.extractHealthStatusFromMeta(metaCodingSystemTag),
        tasks = tasks,
        services = carePlans,
        conditions = defaultRepository.activePatientConditions(patient.logicalId),
        guardians = patient.guardians(),
        practitioners = patient.practitioners(),
        currentAttempt =
          attempt.copy(
            reasons =
              tasks.mapNotNull { task -> task.reasonCode?.codingFirstRep?.display }.distinct(),
          ),
      )
    }
  }

  suspend fun Patient.activeCarePlans() =
    defaultRepository
      .searchResourceFor<CarePlan>(
        subjectId = this.logicalId,
        subjectType = ResourceType.Patient,
        subjectParam = CarePlan.SUBJECT,
      )
      .filter { carePlan -> carePlan.status == CarePlan.CarePlanStatus.ACTIVE }

  suspend fun Patient.practitioners(): List<Practitioner> {
    return generalPractitioner.mapNotNull {
      try {
        if (it.reference == null) return@mapNotNull null
        val id = it.reference.replace("Practitioner/", "")
        fhirEngine.get(ResourceType.Practitioner, id) as Practitioner
      } catch (e: Exception) {
        Timber.e(e)
        null
      }
    }
  }

  suspend fun Patient.guardians(): List<Guardian> =
    this.link
      .filter {
        (it.other.referenceElement.resourceType == ResourceType.RelatedPerson.name).or(
          it.type == Patient.LinkType.REFER &&
            it.other.referenceElement.resourceType == ResourceType.Patient.name,
        )
      }
      .map { defaultRepository.loadResource(it.other) }

  override suspend fun countRegisterFiltered(filters: RegisterFilter): Long {
    return searchRegister(filters, loadAll = true).count().toLong()
  }

  override suspend fun countRegisterData(): Flow<Long> {
    var counter = 0L
    var offset = 0
    val pageCount = 100

    return flow {
      do {
        val tracingPatients =
          fhirEngine.search<Patient> {
            has<Task>(Task.SUBJECT) { filtersForValidTask() }
            from = offset
            count = pageCount
          }
        offset += tracingPatients.size
        val validTracingPatientsCount =
          tracingPatients.map { it.resource }.count { validTasks(it).any() }.toLong()
        counter += validTracingPatientsCount
        emit(counter)
      } while (tracingPatients.isNotEmpty())
    }
  }

  private fun applicationConfiguration(): ApplicationConfiguration =
    configurationRegistry.getAppConfigs()

  private suspend fun validTasks(patient: Patient): List<Task> {
    val patientTasks =
      fhirEngine
        .fetch<Task> {
          filtersForValidTask()
          filter(Task.SUBJECT, { value = patient.referenceValue() })
        }
        .map { it.resource }
    return patientTasks.filter {
      it.status in listOf(Task.TaskStatus.INPROGRESS, Task.TaskStatus.READY) &&
        it.executionPeriod.hasStart() &&
        it.executionPeriod.start
          .before(Date())
          .or(it.executionPeriod.start.asDdMmmYyyy() == Date().asDdMmmYyyy())
    }
  }

  private suspend fun Patient.toTracingRegisterData(
    tasks: Iterable<Task>,
    listResource: ListResource?,
  ): RegisterData.TracingRegisterData {
    val attempt =
      tracingRepository
        .getTracingAttempt(list = listResource)
        .copy(reasons = tasks.mapNotNull { task -> task.reasonCode?.codingFirstRep?.code })

    val oldestTaskDate = tasks.minOfOrNull { it.authoredOn ?: Date() }
    val pregnancyStatus = defaultRepository.getPregnancyStatus(this.logicalId)

    return RegisterData.TracingRegisterData(
      logicalId = this.logicalId,
      name = this.extractName(),
      identifier = this.extractOfficialIdentifier(),
      gender = this.gender,
      familyName = this.extractFamilyName(),
      healthStatus =
        this.extractHealthStatusFromMeta(
          applicationConfiguration().patientTypeFilterTagViaMetaCodingSystem,
        ),
      isPregnant = pregnancyStatus == PregnancyStatus.Pregnant,
      isBreastfeeding = pregnancyStatus == PregnancyStatus.BreastFeeding,
      attempts = attempt.numberOfAttempts,
      lastAttemptDate = attempt.lastAttempt,
      firstAdded = oldestTaskDate,
      reasons = attempt.reasons.distinct(),
    )
  }

  private suspend fun Patient.toTracingRegisterData(): RegisterData.TracingRegisterData {
    val tasks = validTasks(this)
    val listResource = tracingRepository.getPatientListResource(this)
    return this.toTracingRegisterData(tasks, listResource)
  }
}

internal data class PatientTracingItem(
  val patient: Patient,
  val tracingTasks: List<Task>,
  val tracingListResource: ListResource?,
)
