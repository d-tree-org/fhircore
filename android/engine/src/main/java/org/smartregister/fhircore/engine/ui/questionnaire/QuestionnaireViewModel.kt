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

package org.smartregister.fhircore.engine.ui.questionnaire

import android.content.Context
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import ca.uhn.fhir.rest.gclient.TokenClientParam
import ca.uhn.fhir.rest.param.ParamPrefixEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.extensions.logicalId
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.datacapture.mapping.StructureMapExtractionContext
import com.google.android.fhir.search.Operation
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.search
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.context.IWorkerContext
import org.hl7.fhir.r4.context.SimpleWorkerContext
import org.hl7.fhir.r4.model.Appointment
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CarePlan
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Group
import org.hl7.fhir.r4.model.ListResource
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.RelatedPerson
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.StructureMap
import org.hl7.fhir.r4.model.Task
import org.hl7.fhir.r4.model.Task.TaskStatus
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.data.local.DefaultRepository
import org.smartregister.fhircore.engine.task.FhirCarePlanGenerator
import org.smartregister.fhircore.engine.trace.PerformanceReporter
import org.smartregister.fhircore.engine.util.AssetUtil
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import org.smartregister.fhircore.engine.util.TracingHelpers
import org.smartregister.fhircore.engine.util.extension.addTags
import org.smartregister.fhircore.engine.util.extension.asReference
import org.smartregister.fhircore.engine.util.extension.assertSubject
import org.smartregister.fhircore.engine.util.extension.deleteRelatedResources
import org.smartregister.fhircore.engine.util.extension.extractId
import org.smartregister.fhircore.engine.util.extension.extractLogicalIdUuid
import org.smartregister.fhircore.engine.util.extension.filterByResourceTypeId
import org.smartregister.fhircore.engine.util.extension.find
import org.smartregister.fhircore.engine.util.extension.isExtractionCandidate
import org.smartregister.fhircore.engine.util.extension.isIn
import org.smartregister.fhircore.engine.util.extension.prepareQuestionsForReadingOrEditing
import org.smartregister.fhircore.engine.util.extension.referenceValue
import org.smartregister.fhircore.engine.util.extension.retainMetadata
import org.smartregister.fhircore.engine.util.extension.setPropertySafely
import org.smartregister.fhircore.engine.util.extension.toCoding
import org.smartregister.fhircore.engine.util.helper.TransformSupportServices
import timber.log.Timber

@HiltViewModel
@OptIn(FlowPreview::class)
open class QuestionnaireViewModel
@Inject
constructor(
  val fhirEngine: FhirEngine,
  val defaultRepository: DefaultRepository,
  val configurationRegistry: ConfigurationRegistry,
  val transformSupportServices: TransformSupportServices,
  val simpleWorkerContext: SimpleWorkerContext,
  val dispatcherProvider: DispatcherProvider,
  val sharedPreferencesHelper: SharedPreferencesHelper,
  var tracer: PerformanceReporter,
) : ViewModel() {
  @Inject lateinit var fhirCarePlanGenerator: FhirCarePlanGenerator

  val extractionProgress = MutableLiveData<ExtractionProgress>()

  val extractionProgressMessage = MutableLiveData<String>()

  var editQuestionnaireResponse: QuestionnaireResponse? = null

  var structureMapProvider: (suspend (String, IWorkerContext) -> StructureMap?)? = null

  private lateinit var questionnaireConfig: QuestionnaireConfig

  private val jsonParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()

  private val practitionerId: String? by lazy {
    sharedPreferencesHelper
      .read(SharedPreferenceKey.PRACTITIONER_ID.name, null)
      ?.extractLogicalIdUuid()
  }

  private val extractAndSaveRequestStateFlow: MutableStateFlow<suspend () -> Unit> =
    MutableStateFlow {}

  init {
    viewModelScope.launch(dispatcherProvider.io()) {
      extractAndSaveRequestStateFlow.debounce(800.milliseconds).collect {
        it.invoke() // invoke request
      }
    }
  }

  suspend fun loadQuestionnaire(id: String, type: QuestionnaireType): Questionnaire? {
    var questionnaire = ContentCache.getResource(ResourceType.Questionnaire.name + "/" + id)?.copy()

    if (questionnaire == null) {
      questionnaire =
        defaultRepository
          .loadResource<Questionnaire>(id)
          ?.apply {
            if (type.isReadOnly() || type.isEditMode()) {
              item.prepareQuestionsForReadingOrEditing(
                QUESTIONNAIRE_RESPONSE_ITEM,
                type.isReadOnly(),
              )
            }

            // TODO https://github.com/opensrp/fhircore/issues/991#issuecomment-1027872061
            this.url = this.url ?: this.referenceValue()
          }
          ?.also {
            ContentCache.saveResource(
              id,
              it.copy(),
            )
          }
    }

    return questionnaire as? Questionnaire
  }

  suspend fun getQuestionnaireConfig(form: String, context: Context): QuestionnaireConfig {
    val loadConfig =
      loadQuestionnaireConfigFromRegistry() ?: loadQuestionnaireConfigFromAssets(context)
    questionnaireConfig = loadConfig!!.first { it.form == form }
    return questionnaireConfig
  }

  @Throws(QuestionnaireNotFoundException::class)
  suspend fun getQuestionnaireConfigPair(
    context: Context,
    formName: String,
    type: QuestionnaireType,
  ): Pair<QuestionnaireConfig, Questionnaire> {
    return try {
      val config = getQuestionnaireConfig(formName, context)
      val questionnaire = loadQuestionnaire(config.identifier, type)!!
      Pair(config, questionnaire)
    } catch (e: Exception) {
      // load questionnaire from db and build config
      val questionnaire =
        loadQuestionnaire(formName, type) ?: throw QuestionnaireNotFoundException(formName)
      questionnaireConfig =
        QuestionnaireConfig(
          form = questionnaire.name ?: "",
          title = questionnaire.title ?: "",
          identifier = questionnaire.logicalId,
        )
      Pair(questionnaireConfig, questionnaire)
    }
  }

  private fun loadQuestionnaireConfigFromRegistry(): List<QuestionnaireConfig>? {
    return kotlin.runCatching { configurationRegistry.getFormConfigs() }.getOrNull()
  }

  private suspend fun loadQuestionnaireConfigFromAssets(
    context: Context,
  ): List<QuestionnaireConfig>? =
    kotlin
      .runCatching {
        withContext(dispatcherProvider.io()) {
          AssetUtil.decodeAsset<List<QuestionnaireConfig>>(
            fileName = QuestionnaireActivity.FORM_CONFIGURATIONS,
            context = context,
          )
        }
      }
      .getOrNull()

  suspend fun fetchStructureMap(structureMapUrl: String?): StructureMap? {
    var structureMap: Resource? = null
    structureMapUrl?.substringAfterLast("/")?.run {
      structureMap = ContentCache.getResource(ResourceType.StructureMap.name + "/" + this)
      structureMap =
        structureMap
          ?: defaultRepository.loadResource<StructureMap>(this)?.also {
            it.let { ContentCache.saveResource(this, it) }
          }
    }
    return structureMap as? StructureMap
  }

  fun appendOrganizationInfo(resource: Resource) {
    sharedPreferencesHelper.organisationCode()?.let { org ->
      val organizationRef =
        Reference().apply { reference = "${ResourceType.Organization.name}/$org" }

      if (resource is Patient && !resource.hasManagingOrganization()) {
        resource.managingOrganization = organizationRef
      } else if (resource is Group && !resource.hasManagingEntity()) {
        resource.managingEntity = organizationRef
      }
    }
  }

  fun appendPractitionerInfo(resource: Resource) {
    practitionerId?.let {
      val practitionerRef = it.asReference(ResourceType.Practitioner)

      if (resource is Encounter) {
        resource.participant =
          arrayListOf(
            Encounter.EncounterParticipantComponent().apply { individual = practitionerRef },
          )
      }
      //      else if (resource is Patient) {
      //        if (resource.hasGeneralPractitioner()) {
      //          if (!resource.generalPractitioner.contains(practitionerRef)) {
      //            resource.addGeneralPractitioner(practitionerRef)
      //          }
      //        } else {
      //          resource.generalPractitioner = arrayListOf(practitionerRef)
      //        }
      //      }
    }
  }

  suspend fun appendPatientsAndRelatedPersonsToGroups(resource: Resource, groupResourceId: String) {
    defaultRepository.loadResource<Group>(groupResourceId)?.run {
      if (resource.resourceType == ResourceType.Patient) {
        this.member?.add(
          Group.GroupMemberComponent().apply {
            entity =
              Reference().apply { reference = "${ResourceType.Patient.name}/${resource.logicalId}" }
          },
        )
      } else {
        this.managingEntity =
          Reference().apply {
            reference = "${ResourceType.RelatedPerson.name}/${resource.logicalId}"
          }
      }
      defaultRepository.addOrUpdate(true, this)
    }
  }

  fun extractAndSaveResources(
    context: Context,
    resourceId: String?,
    groupResourceId: String? = null,
    questionnaireResponse: QuestionnaireResponse,
    questionnaireType: QuestionnaireType = QuestionnaireType.DEFAULT,
    questionnaire: Questionnaire,
    backReference: String?,
  ) {
    val request = suspend {
      try {
        val extras =
          doExtractAndSaveResources(
            context,
            resourceId,
            groupResourceId,
            questionnaireResponse,
            questionnaireType,
            questionnaire,
            backReference,
          )
        extractionProgress.postValue(ExtractionProgress.Success(questionnaireResponse, extras))
      } catch (e: Exception) {
        Timber.e(e)
        extractionProgress.postValue(ExtractionProgress.Failed(questionnaireResponse, e))
      }
    }

    extractAndSaveRequestStateFlow.value = request
  }

  private suspend fun doExtractAndSaveResources(
    context: Context,
    resourceId: String?,
    groupResourceId: String? = null,
    questionnaireResponse: QuestionnaireResponse,
    questionnaireType: QuestionnaireType = QuestionnaireType.DEFAULT,
    questionnaire: Questionnaire,
    backReference: String?,
  ): List<Resource> {
    tracer.startTrace(QUESTIONNAIRE_TRACE)
    // important to set response subject so that structure map can handle subject for all entities
    handleQuestionnaireResponseSubject(resourceId, questionnaire, questionnaireResponse)
    Timber.e(jsonParser.encodeResourceToString(questionnaireResponse))
    val extras = mutableListOf<Resource>()
    if (questionnaire.isExtractionCandidate()) {
      val bundle = performExtraction(context, questionnaire, questionnaireResponse)
      questionnaireResponse.contained = mutableListOf()
      bundle.entry.forEach { bundleEntry ->
        // NOTE: Some entry is null in a weird case
        if (bundleEntry.resource == null) return@forEach
        // add organization to entities representing individuals in registration questionnaire
        if (bundleEntry.resource.resourceType.isIn(ResourceType.Patient, ResourceType.Group)) {
          if (questionnaireConfig.setOrganizationDetails) {
            appendOrganizationInfo(bundleEntry.resource)
          }
          // if it is new registration set response subject
          if (resourceId == null) {
            questionnaireResponse.subject = bundleEntry.resource.asReference()
          }
        }
        if (questionnaireConfig.setPractitionerDetails) {
          appendPractitionerInfo(bundleEntry.resource)
        }

        if (
          questionnaireType != QuestionnaireType.EDIT &&
            bundleEntry.resource.resourceType.isIn(
              ResourceType.Patient,
              ResourceType.RelatedPerson,
            )
        ) {
          groupResourceId?.let {
            appendPatientsAndRelatedPersonsToGroups(
              resource = bundleEntry.resource,
              groupResourceId = it,
            )
          }
        }

        // response MUST have subject by far otherwise flow has issues
        if (!questionnaire.experimental) questionnaireResponse.assertSubject()

        // TODO https://github.com/opensrp/fhircore/issues/900
        // for edit mode replace client and resource subject ids.
        // Ideally ResourceMapper should allow this internally via structure-map
        if (questionnaireType.isEditMode()) {
          if (bundleEntry.resource.resourceType.isIn(ResourceType.Patient, ResourceType.Group)) {
            bundleEntry.resource.id = questionnaireResponse.subject.extractId()
          } else {
            bundleEntry.resource.setPropertySafely("subject", questionnaireResponse.subject)
            bundleEntry.resource.setPropertySafely("patient", questionnaireResponse.subject)
          }
        }
        questionnaireResponse.contained.add(bundleEntry.resource)

        if (bundleEntry.resource is Encounter) extras.add(bundleEntry.resource)

        if (
          (bundleEntry.resource is CarePlan || bundleEntry.resource is Patient) &&
            bundleEntry.resource.meta.tag.isNotEmpty()
        ) {
          carePlanAndPatientMetaExtraction(bundleEntry.resource)
        }
      }

      if (questionnaire.experimental) {
        Timber.w(
          "${questionnaire.name}(${questionnaire.logicalId}) is experimental and not save any data",
        )
      } else {
        saveBundleResources(bundle)
      }

      if (questionnaireType.isEditMode() && editQuestionnaireResponse != null) {
        questionnaireResponse.retainMetadata(editQuestionnaireResponse!!)
      }

      saveQuestionnaireResponse(questionnaire, questionnaireResponse)
      // TODO https://github.com/opensrp/fhircore/issues/900
      // reassess following i.e. deleting/updating older resources because one resource
      // might have generated other flow in subsequent followups
      if (questionnaireType.isEditMode() && editQuestionnaireResponse != null) {
        editQuestionnaireResponse!!.deleteRelatedResources(defaultRepository)
      }
    } else {
      saveQuestionnaireResponse(questionnaire, questionnaireResponse)
    }
    updateCarePlanAndTask(extras, backReference)
    tracer.stopTrace(QUESTIONNAIRE_TRACE)
    return extras
  }

  suspend fun carePlanAndPatientMetaExtraction(source: Resource) {
    try {
      /** Get a FHIR [Resource] in the local storage. */
      var resource = fhirEngine.get(source.resourceType, source.id)
      /** Increment [Resource.meta] versionId of [source]. */
      resource.meta.versionId?.toInt()?.plus(1)?.let {
        /** Append passed [Resource.meta] to the [source]. */
        resource.addTags(source.meta.tag)
        /** Assign [Resource.meta] versionId of [source]. */
        resource = resource.copy().apply { meta.versionId = "$it" }
        /** Delete a FHIR [source] in the local storage. */
        fhirEngine.delete(resource.resourceType, resource.id)
        /** Recreate a FHIR [source] in the local storage. */
        fhirEngine.create(resource)
      }
    } catch (e: Exception) {
      Timber.e(e)
    }
  }

  /**
   * Sets questionnaireResponse subject with proper subject-type defined in questionnaire with an
   * existing resourceId or organization or null
   */
  fun handleQuestionnaireResponseSubject(
    resourceId: String?,
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ) {
    val subjectType = questionnaire.subjectType.firstOrNull()?.code ?: ResourceType.Patient.name
    questionnaireResponse.subject =
      when (subjectType) {
        ResourceType.Organization.name ->
          sharedPreferencesHelper.organisationCode()?.asReference(ResourceType.Organization)
        else -> resourceId?.asReference(ResourceType.valueOf(subjectType))
      }
  }

  private suspend fun updateCarePlanAndTask(extras: List<Resource>, backReference: String?) {
    if (backReference != null && backReference != "notify") {
      extras.forEach {
        if (it is Encounter) {
          fhirCarePlanGenerator.completeTask(
            backReference.asReference(ResourceType.Task).extractId(),
            it.status,
          )
        }
      }
    }
  }

  suspend fun saveQuestionnaireResponse(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ) {
    if (questionnaire.experimental) {
      Timber.w(
        "${questionnaire.name}(${questionnaire.logicalId}) is experimental and not save any data",
      )
      return
    }

    questionnaireResponse.assertSubject() // should not allow further flow without subject

    questionnaireResponse.questionnaire = "${questionnaire.resourceType}/${questionnaire.logicalId}"

    if (questionnaireResponse.logicalId.isEmpty()) {
      questionnaireResponse.id = UUID.randomUUID().toString()
      questionnaireResponse.authored = Date()
    }

    questionnaire.useContext
      .filter { it.hasValueCodeableConcept() }
      .forEach { it.valueCodeableConcept.coding.forEach { questionnaireResponse.meta.addTag(it) } }

    defaultRepository.addOrUpdate(true, questionnaireResponse)
  }

  suspend fun performExtraction(
    context: Context,
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ): Bundle {
    return ResourceMapper.extract(
      questionnaire = questionnaire,
      questionnaireResponse = questionnaireResponse,
      StructureMapExtractionContext(
        transformSupportServices = transformSupportServices,
        structureMapProvider = retrieveStructureMapProvider(),
        workerContext = simpleWorkerContext,
      ),
    )
  }

  suspend fun saveBundleResources(bundle: Bundle) {
    if (!bundle.isEmpty) {
      bundle.entry.forEach {
        if (it.resource == null) return@forEach
        defaultRepository.addOrUpdate(true, it.resource)
      }
    }
  }

  fun retrieveStructureMapProvider(): (suspend (String, IWorkerContext) -> StructureMap?) {
    if (structureMapProvider == null) {
      structureMapProvider = { structureMapUrl: String, _: IWorkerContext ->
        fetchStructureMap(structureMapUrl)
      }
    }

    return structureMapProvider!!
  }

  suspend fun loadPatient(patientId: String): Patient? {
    return defaultRepository.loadResource(patientId)
  }

  suspend fun loadRelatedPerson(patientId: String): List<RelatedPerson>? {
    return defaultRepository.loadRelatedPersons(patientId)
  }

  private suspend fun loadScheduledAppointments(patientId: String): Iterable<Appointment> {
    return fhirEngine
      .search<Appointment> {
        filter(
          Appointment.STATUS,
          { value = of(Appointment.AppointmentStatus.BOOKED.toCode()) },
          { value = of(Appointment.AppointmentStatus.WAITLIST.toCode()) },
          operation = Operation.OR,
        )
      }
      .map { it.resource }
      // filter on patient subject
      .filter { appointment ->
        appointment.participant.any {
          it.hasActor() &&
            it.actor.referenceElement.resourceType == ResourceType.Patient.name &&
            it.actor.referenceElement.idPart == patientId
        }
      }
      .filter {
        (it.status == Appointment.AppointmentStatus.BOOKED ||
          it.status == Appointment.AppointmentStatus.WAITLIST) &&
          it.hasStart() &&
          it.start.after(
            Date.from(
              LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().minusSeconds(30),
            ),
          )
      }
  }

  private suspend fun getLastActiveCarePlan(patientId: String): CarePlan? {
    val carePlans =
      fhirEngine
        .search<CarePlan> {
          filterByResourceTypeId(CarePlan.SUBJECT, ResourceType.Patient, patientId)
          filter(
            CarePlan.STATUS,
            { value = of(CarePlan.CarePlanStatus.COMPLETED.toCoding()) },
            operation = Operation.OR,
          )
        }
        .map { it.resource }
    return carePlans.sortedByDescending { it.meta.lastUpdated }.firstOrNull()
  }

  private suspend fun getActiveListResource(patient: String): ListResource? {
    val list =
      fhirEngine
        .search<ListResource> {
          filter(ListResource.SUBJECT, { value = "Patient/$patient" })
          filter(ListResource.STATUS, { value = of(ListResource.ListStatus.CURRENT.toCode()) })
          sort(ListResource.TITLE, Order.DESCENDING)
          count = 1
          from = 0
        }
        .map { it.resource }
    return list.firstOrNull()
  }

  suspend fun loadLatestAppointmentWithNoStartDate(patientId: String): Appointment? {
    return fhirEngine
      .search<Appointment> {
        filter(Appointment.STATUS, { value = of(Appointment.AppointmentStatus.BOOKED.toCode()) })
      }
      .map { it.resource }
      // filter on patient subject
      .filter { appointment ->
        appointment.participant.any {
          it.hasActor() &&
            it.actor.referenceElement.resourceType == ResourceType.Patient.name &&
            it.actor.referenceElement.idPart == patientId
        }
      }
      .filterNot { it.hasStart() && it.status == Appointment.AppointmentStatus.BOOKED }
      .sortedBy { it.created }
      .firstOrNull()
  }

  suspend fun loadTracing(patientId: String): List<Task> {
    val tasks =
      fhirEngine
        .search<Task> {
          filter(Task.SUBJECT, { value = "Patient/$patientId" })
          filter(
            TokenClientParam("code"),
            {
              value =
                of(CodeableConcept().addCoding(Coding("http://snomed.info/sct", "225368008", null)))
            },
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
              value = of(DateTimeType.now())
              prefix = ParamPrefixEnum.GREATERTHAN
            },
          )
        }
        .map { it.resource }
    return tasks.filter { it.status in arrayOf(TaskStatus.READY, TaskStatus.INPROGRESS) }
  }

  fun saveResource(resource: Resource) {
    viewModelScope.launch { defaultRepository.save(resource = resource) }
  }

  fun extractRelevantObservation(
    resource: Bundle,
    questionnaireLogicalId: String,
  ): Bundle {
    val bundle = Bundle()
    resource.entry.forEach { entry ->
      if (entry.resource is Observation) {
        val code = (entry.resource as Observation).code.coding.first().code.toString()
        if (code.contains(questionnaireLogicalId)) bundle.addEntry(entry)
      } else bundle.addEntry(entry)
    }
    return bundle
  }

  private fun getPopulationResourcesFromIntent(
    intent: Intent,
  ): List<Resource> {
    return intent
      .getStringArrayListExtra(QuestionnaireActivity.QUESTIONNAIRE_POPULATION_RESOURCES)
      ?.map { jsonParser.parseResource(it) as Resource } ?: listOf()
  }

  open suspend fun getPopulationResources(
    intent: Intent,
    questionnaireLogicalId: String,
  ): Array<Resource> {
    val resourcesList = getPopulationResourcesFromIntent(intent).toMutableList()

    intent.getStringExtra(QuestionnaireActivity.QUESTIONNAIRE_ARG_PATIENT_KEY)?.let { patientId ->
      loadPatient(patientId)?.apply { resourcesList.add(this) }
        ?: defaultRepository.loadResource<Group>(patientId)?.apply { resourcesList.add(this) }

      val bundleIndex = resourcesList.indexOfFirst { x -> x is Bundle }
      if (bundleIndex != -1) {
        val currentBundle = resourcesList[bundleIndex] as Bundle

        if (TracingHelpers.requireTracingTasks(questionnaireConfig.identifier)) {
          val bundle = Bundle()
          bundle.id = TracingHelpers.tracingBundleId
          val tasks = loadTracing(patientId)
          tasks.forEach { bundle.addEntry(Bundle.BundleEntryComponent().setResource(it)) }

          val list = getActiveListResource(patientId)
          if (list != null) {
            bundle.addEntry(Bundle.BundleEntryComponent().setResource(list))
          }

          currentBundle.addEntry(
            Bundle.BundleEntryComponent().setResource(bundle).apply {
              id = TracingHelpers.tracingBundleId
            },
          )
        }

        val appointmentToPopulate = loadLatestAppointmentWithNoStartDate(patientId)
        if (appointmentToPopulate != null) {
          currentBundle.addEntry(Bundle.BundleEntryComponent().setResource(appointmentToPopulate))
        }
        // Add appointments that may need to be closed
        loadScheduledAppointments(patientId).forEach {
          currentBundle.addEntry(Bundle.BundleEntryComponent().setResource(it))
        }

        val lastCarePlan = getLastActiveCarePlan(patientId)
        if (lastCarePlan != null) {
          currentBundle.addEntry(Bundle.BundleEntryComponent().setResource(lastCarePlan))
        }

        resourcesList[bundleIndex] = currentBundle
      }

      // for situations where patient RelatedPersons not passed through intent extras
      if (resourcesList.none { it.resourceType == ResourceType.RelatedPerson }) {
        loadRelatedPerson(patientId)?.forEach { resourcesList.add(it) }
      }
    }
    return resourcesList.toTypedArray()
  }

  suspend fun generateQuestionnaireResponse(
    questionnaire: Questionnaire,
    intent: Intent,
  ): QuestionnaireResponse {
    val populationResourcesList = getPopulationResources(intent, questionnaire.logicalId)
    val populationResourceTypeResourceMap =
      populationResourcesList.associateBy { it.resourceType.name.lowercase() }
    val questResponse = ResourceMapper.populate(questionnaire, populationResourceTypeResourceMap)
    questResponse.contained = populationResourcesList.toList()
    questResponse.questionnaire = "${questionnaire.resourceType}/${questionnaire.logicalId}"
    return questResponse
  }

  fun getAgeInput(questionnaireResponse: QuestionnaireResponse): Int? {
    return questionnaireResponse
      .find(QuestionnaireActivity.QUESTIONNAIRE_AGE)
      ?.answer
      ?.firstOrNull()
      ?.valueDecimalType
      ?.value
      ?.toInt()
  }

  /** Subtract [age] from today's date */
  fun calculateDobFromAge(age: Int): Date =
    Calendar.getInstance()
      .apply {
        add(Calendar.YEAR, -age)
        set(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.MONTH, 1)
      }
      .time

  companion object {
    private const val QUESTIONNAIRE_TRACE = "Questionnaire-extractAndSaveResources"
    private const val QUESTIONNAIRE_RESPONSE_ITEM = "QuestionnaireResponse.item"
  }
}
