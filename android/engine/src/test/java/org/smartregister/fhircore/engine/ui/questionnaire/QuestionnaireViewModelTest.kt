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

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.IParser
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.SearchResult
import com.google.android.fhir.datacapture.extensions.logicalId
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.db.ResourceNotFoundException
import com.google.android.fhir.get
import com.google.android.fhir.search.Search
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.hl7.fhir.r4.context.SimpleWorkerContext
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CanonicalType
import org.hl7.fhir.r4.model.CarePlan
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.DecimalType
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Expression
import org.hl7.fhir.r4.model.Extension
import org.hl7.fhir.r4.model.Group
import org.hl7.fhir.r4.model.HumanName
import org.hl7.fhir.r4.model.Meta
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Period
import org.hl7.fhir.r4.model.Practitioner
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.RelatedPerson
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.r4.model.StructureMap
import org.hl7.fhir.r4.model.Task
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.Shadows
import org.robolectric.util.ReflectionHelpers
import org.smartregister.fhircore.engine.app.fakes.Faker
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.configuration.app.ConfigService
import org.smartregister.fhircore.engine.data.local.DefaultRepository
import org.smartregister.fhircore.engine.data.remote.model.response.UserInfo
import org.smartregister.fhircore.engine.robolectric.RobolectricTest
import org.smartregister.fhircore.engine.rule.CoroutineTestRule
import org.smartregister.fhircore.engine.trace.FakePerformanceReporter
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.LOGGED_IN_PRACTITIONER
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import org.smartregister.fhircore.engine.util.TracingHelpers
import org.smartregister.fhircore.engine.util.USER_INFO_SHARED_PREFERENCE_KEY
import org.smartregister.fhircore.engine.util.extension.addTags
import org.smartregister.fhircore.engine.util.extension.asReference
import org.smartregister.fhircore.engine.util.extension.extractLogicalIdUuid
import org.smartregister.fhircore.engine.util.extension.loadResource
import org.smartregister.fhircore.engine.util.extension.retainMetadata
import org.smartregister.model.practitioner.FhirPractitionerDetails
import org.smartregister.model.practitioner.PractitionerDetails

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class QuestionnaireViewModelTest : RobolectricTest() {

  @Inject lateinit var sharedPreferencesHelper: SharedPreferencesHelper

  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) var instantTaskExecutorRule = InstantTaskExecutorRule()

  @get:Rule(order = 2) var coroutineRule = CoroutineTestRule()

  @Inject lateinit var configService: ConfigService

  @Inject lateinit var dispatcherProvider: DispatcherProvider

  private val fhirEngine: FhirEngine = mockk()

  private val context: Application = ApplicationProvider.getApplicationContext()

  private lateinit var questionnaireViewModel: QuestionnaireViewModel

  private lateinit var defaultRepo: DefaultRepository

  private val configurationRegistry = Faker.buildTestConfigurationRegistry()
  private lateinit var samplePatientRegisterQuestionnaire: Questionnaire
  private lateinit var questionnaireConfig: QuestionnaireConfig

  @Before
  fun setUp() {
    hiltRule.inject()

    sharedPreferencesHelper.write(
      USER_INFO_SHARED_PREFERENCE_KEY,
      getUserInfo(),
      encodeWithGson = true,
    )

    sharedPreferencesHelper.write(
      LOGGED_IN_PRACTITIONER,
      Practitioner().apply { id = "123" },
      encodeWithGson = true,
    )
    sharedPreferencesHelper.write(
      SharedPreferenceKey.PRACTITIONER_ID.name,
      practitionerDetails()
        .fhirPractitionerDetails
        ?.practitioners
        ?.firstOrNull()
        ?.id
        ?.extractLogicalIdUuid(),
    )

    defaultRepo =
      spyk(
        DefaultRepository(
          fhirEngine = fhirEngine,
          dispatcherProvider = dispatcherProvider,
          sharedPreferencesHelper = sharedPreferencesHelper,
          configurationRegistry = configurationRegistry,
          configService = configService,
        ),
      )

    val configurationRegistry = mockk<ConfigurationRegistry>()
    every { configurationRegistry.appId } returns "appId"

    questionnaireViewModel =
      spyk(
        QuestionnaireViewModel(
          fhirEngine = fhirEngine,
          defaultRepository = defaultRepo,
          configurationRegistry = configurationRegistry,
          transformSupportServices = mockk(),
          dispatcherProvider = defaultRepo.dispatcherProvider,
          sharedPreferencesHelper = sharedPreferencesHelper,
          tracer = FakePerformanceReporter(),
        ),
      )
    coEvery { fhirEngine.get(ResourceType.Patient, any()) } returns samplePatient()
    questionnaireConfig = runBlocking {
      questionnaireViewModel.getQuestionnaireConfig(
        "patient-registration",
        ApplicationProvider.getApplicationContext(),
      )
    }

    coEvery { fhirEngine.create(any()) } answers { listOf() }
    coEvery { fhirEngine.update(any()) } answers {}

    coEvery { defaultRepo.save(any()) } returns Unit
    coEvery { defaultRepo.addOrUpdate(resource = any()) } just runs

    // Setup sample resources
    val iParser: IParser = FhirContext.forR4Cached().newJsonParser()
    val qJson =
      context.assets.open("sample_patient_registration.json").bufferedReader().use { it.readText() }

    samplePatientRegisterQuestionnaire = iParser.parseResource(qJson) as Questionnaire
  }

  @Test
  fun testLoadQuestionnaireShouldCallDefaultRepoLoadResource() {
    coEvery { fhirEngine.get(ResourceType.Questionnaire, "12345") } returns
      Questionnaire().apply { id = "12345" }

    val result = runBlocking {
      questionnaireViewModel.loadQuestionnaire("12345", QuestionnaireType.DEFAULT)
    }

    coVerify { fhirEngine.get(ResourceType.Questionnaire, "12345") }
    Assert.assertEquals("12345", result!!.logicalId)
  }

  @Test
  fun testLoadQuestionnaireShouldSetQuestionnaireQuestionsToReadOnly() {
    val questionnaire =
      Questionnaire().apply {
        id = "12345"
        item =
          listOf(
            Questionnaire.QuestionnaireItemComponent().apply {
              type = Questionnaire.QuestionnaireItemType.GROUP
              linkId = "q1-grp"
              item =
                listOf(
                  Questionnaire.QuestionnaireItemComponent().apply {
                    type = Questionnaire.QuestionnaireItemType.TEXT
                    linkId = "q1-name"
                  },
                )
            },
            Questionnaire.QuestionnaireItemComponent().apply {
              type = Questionnaire.QuestionnaireItemType.CHOICE
              linkId = "q2-gender"
            },
            Questionnaire.QuestionnaireItemComponent().apply {
              type = Questionnaire.QuestionnaireItemType.DATE
              linkId = "q3-date"
            },
          )
      }
    coEvery { fhirEngine.get(ResourceType.Questionnaire, "12345") } returns questionnaire

    ReflectionHelpers.setField(questionnaireViewModel, "defaultRepository", defaultRepo)

    val result = runBlocking {
      questionnaireViewModel.loadQuestionnaire("12345", QuestionnaireType.READ_ONLY)
    }

    Assert.assertTrue(result!!.item[0].item[0].readOnly)
    Assert.assertEquals("q1-name", result.item[0].item[0].linkId)
    Assert.assertTrue(result.item[1].readOnly)
    Assert.assertEquals("q2-gender", result.item[1].linkId)
    Assert.assertTrue(result.item[2].readOnly)
    Assert.assertEquals("q3-date", result.item[2].linkId)
  }

  @Test
  fun testLoadQuestionnaireShouldMakeQuestionsReadOnlyAndAddInitialExpressionExtension() {
    val questionnaire =
      Questionnaire().apply {
        id = "12345"
        item =
          listOf(
            Questionnaire.QuestionnaireItemComponent().apply {
              linkId = "patient-first-name"
              type = Questionnaire.QuestionnaireItemType.TEXT
              item =
                listOf(
                  Questionnaire.QuestionnaireItemComponent().apply {
                    linkId = "patient-last-name"
                    type = Questionnaire.QuestionnaireItemType.TEXT
                  },
                )
            },
            Questionnaire.QuestionnaireItemComponent().apply {
              linkId = "patient-age"
              type = Questionnaire.QuestionnaireItemType.INTEGER
            },
            Questionnaire.QuestionnaireItemComponent().apply {
              linkId = "patient-contact"
              type = Questionnaire.QuestionnaireItemType.GROUP
              item =
                listOf(
                  Questionnaire.QuestionnaireItemComponent().apply {
                    linkId = "patient-dob"
                    type = Questionnaire.QuestionnaireItemType.DATE
                  },
                  Questionnaire.QuestionnaireItemComponent().apply {
                    linkId = "patient-related-person"
                    type = Questionnaire.QuestionnaireItemType.GROUP
                    item =
                      listOf(
                        Questionnaire.QuestionnaireItemComponent().apply {
                          linkId = "rp-name"
                          type = Questionnaire.QuestionnaireItemType.TEXT
                        },
                      )
                  },
                )
            },
          )
      }

    coEvery { fhirEngine.get(ResourceType.Questionnaire, "12345") } returns questionnaire

    val result = runBlocking {
      questionnaireViewModel.loadQuestionnaire("12345", QuestionnaireType.READ_ONLY)
    }

    Assert.assertEquals("12345", result!!.logicalId)
    Assert.assertTrue(result.item[0].readOnly)
    Assert.assertEquals("patient-first-name", result.item[0].linkId)
    Assert.assertEquals("patient-last-name", result.item[0].item[0].linkId)
    Assert.assertTrue(result.item[1].readOnly)
    Assert.assertFalse(result.item[2].readOnly)
    Assert.assertEquals(0, result.item[2].extension.size)
    Assert.assertTrue(result.item[2].item[0].readOnly)
    Assert.assertFalse(result.item[2].item[1].readOnly)
    Assert.assertTrue(result.item[2].item[1].item[0].readOnly)
  }

  @Test
  fun testLoadQuestionnaireShouldMakeQuestionsEditableAndAddInitialExpressionExtension() {
    val questionnaire =
      Questionnaire().apply {
        id = "12345"
        item =
          listOf(
            Questionnaire.QuestionnaireItemComponent().apply {
              linkId = "patient-first-name"
              type = Questionnaire.QuestionnaireItemType.TEXT
              item =
                listOf(
                  Questionnaire.QuestionnaireItemComponent().apply {
                    linkId = "patient-last-name"
                    type = Questionnaire.QuestionnaireItemType.TEXT
                  },
                )
            },
            Questionnaire.QuestionnaireItemComponent().apply {
              linkId = "patient-age"
              type = Questionnaire.QuestionnaireItemType.INTEGER
            },
            Questionnaire.QuestionnaireItemComponent().apply {
              linkId = "patient-contact"
              type = Questionnaire.QuestionnaireItemType.GROUP
              item =
                listOf(
                  Questionnaire.QuestionnaireItemComponent().apply {
                    linkId = "patient-dob"
                    type = Questionnaire.QuestionnaireItemType.DATE
                  },
                  Questionnaire.QuestionnaireItemComponent().apply {
                    linkId = "patient-related-person"
                    type = Questionnaire.QuestionnaireItemType.GROUP
                    item =
                      listOf(
                        Questionnaire.QuestionnaireItemComponent().apply {
                          linkId = "rp-name"
                          type = Questionnaire.QuestionnaireItemType.TEXT
                        },
                      )
                  },
                )
            },
          )
      }

    coEvery { fhirEngine.get(ResourceType.Questionnaire, "12345") } returns questionnaire

    val result = runBlocking {
      questionnaireViewModel.loadQuestionnaire("12345", QuestionnaireType.EDIT)
    }

    Assert.assertEquals("12345", result!!.logicalId)
    Assert.assertFalse(result.item[0].readOnly)
    Assert.assertEquals("patient-first-name", result.item[0].linkId)
    Assert.assertEquals("patient-last-name", result.item[0].item[0].linkId)
    Assert.assertFalse(result.item[1].readOnly)
    Assert.assertFalse(result.item[2].readOnly)
    Assert.assertEquals(0, result.item[2].extension.size)
    Assert.assertFalse(result.item[2].item[0].readOnly)
    Assert.assertFalse(result.item[2].item[1].readOnly)
    Assert.assertFalse(result.item[2].item[1].item[0].readOnly)
  }

  @Test
  fun testGetQuestionnaireConfigShouldLoadRightConfig() {
    val result = runBlocking {
      questionnaireViewModel.getQuestionnaireConfig("patient-registration", context)
    }
    Assert.assertEquals("patient-registration", result.form)
    Assert.assertEquals("Add Patient", result.title)
    Assert.assertEquals("207", result.identifier)
  }

  @Test(expected = QuestionnaireNotFoundException::class)
  fun `getQuestionnaireConfigPair throws 'QuestionnaireNotFoundException' when questionnaire not found`() =
    runTest {
      val formName = "missing_form"
      coEvery { fhirEngine.get<Questionnaire>(formName) } throws
        ResourceNotFoundException(ResourceType.Questionnaire.name, formName)
      questionnaireViewModel.getQuestionnaireConfigPair(
        context,
        formName,
        type = QuestionnaireType.DEFAULT,
      )
    }

  @Test
  fun `getQuestionnaireConfigPair returns correct config and questionnaire`() = runTest {
    val formName = "patient-registration"
    val config = questionnaireViewModel.getQuestionnaireConfig(formName, context)
    coEvery { fhirEngine.loadResource<Questionnaire>(eq(config.identifier)) } returns
      samplePatientRegisterQuestionnaire

    val result =
      questionnaireViewModel.getQuestionnaireConfigPair(
        context,
        formName,
        type = QuestionnaireType.DEFAULT,
      )
    Assert.assertEquals(config, result.first)
    Assert.assertEquals(samplePatientRegisterQuestionnaire, result.second)
  }

  @Test
  fun testExtractAndSaveResourcesWithTargetStructureMapShouldCallExtractionService() {
    mockkObject(ResourceMapper)
    val patient = samplePatient()

    coEvery { fhirEngine.get(ResourceType.Patient, any()) } returns Patient()
    coEvery { fhirEngine.get(ResourceType.StructureMap, any()) } returns StructureMap()
    coEvery { fhirEngine.get(ResourceType.Group, any()) } returns Group()
    coEvery { ResourceMapper.extract(any(), any(), any()) } returns
      Bundle().apply { addEntry().apply { this.resource = patient } }

    val questionnaire =
      Questionnaire().apply {
        addUseContext().apply {
          code = Coding().apply { code = "focus" }
          value = CodeableConcept().apply { addCoding().apply { code = "1234567" } }
        }
        addExtension(
          "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-targetStructureMap",
          CanonicalType("1234"),
        )
      }

    Shadows.shadowOf(Looper.getMainLooper()).idle()

    runTest {
      val questionnaireResponse = QuestionnaireResponse()

      questionnaireViewModel.extractAndSaveResources(
        context = context,
        resourceId = "12345",
        questionnaireResponse = questionnaireResponse,
        questionnaire = questionnaire,
        backReference =
          intent.getStringExtra(QuestionnaireActivity.QUESTIONNAIRE_BACK_REFERENCE_KEY),
      )

      coVerify { defaultRepo.addOrUpdate(resource = patient) }
      coVerify { defaultRepo.addOrUpdate(resource = questionnaireResponse) }
      coVerify(timeout = 10000) { ResourceMapper.extract(any(), any(), any()) }
    }
    unmockkObject(ResourceMapper)
  }

  @Test
  fun testExtractAndSaveResourcesWithExtractionExtensionAndNullResourceShouldAssignTags() {
    val questionnaire =
      Questionnaire().apply {
        addUseContext().apply {
          code = Coding().apply { code = "focus" }
          value = CodeableConcept().apply { addCoding().apply { code = "1234567" } }
        }
        addExtension(
          Extension(
            "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-itemExtractionContext",
            Expression().apply {
              language = "application/x-fhir-query"
              expression = "Patient"
              name = "Patient"
            },
          ),
        )
      }

    val questionnaireResponse = QuestionnaireResponse().apply { subject = Reference("12345") }

    questionnaireViewModel.extractAndSaveResources(
      context = context,
      resourceId = null,
      questionnaireResponse = questionnaireResponse,
      questionnaire = questionnaire,
      backReference = intent.getStringExtra(QuestionnaireActivity.QUESTIONNAIRE_BACK_REFERENCE_KEY),
    )

    coVerify { defaultRepo.addOrUpdate(resource = any()) }

    unmockkObject(ResourceMapper)
  }

  @Test
  fun testExtractAndSaveResourcesWithResourceIdShouldSaveQuestionnaireResponse() {
    coEvery { fhirEngine.get(ResourceType.Patient, "12345") } returns samplePatient()

    val questionnaireResponseSlot = slot<QuestionnaireResponse>()
    val questionnaire =
      Questionnaire().apply {
        addUseContext().apply {
          code = Coding().apply { code = "focus" }
          value = CodeableConcept().apply { addCoding().apply { code = "1234567" } }
        }
      }

    questionnaireViewModel.extractAndSaveResources(
      context = context,
      resourceId = "12345",
      questionnaireResponse = QuestionnaireResponse(),
      questionnaire = questionnaire,
      backReference = intent.getStringExtra(QuestionnaireActivity.QUESTIONNAIRE_BACK_REFERENCE_KEY),
    )

    coVerify(timeout = 2000) {
      defaultRepo.addOrUpdate(resource = capture(questionnaireResponseSlot))
    }

    Assert.assertEquals(
      "12345",
      questionnaireResponseSlot.captured.subject.reference.replace("Patient/", ""),
    )
    Assert.assertEquals("1234567", questionnaireResponseSlot.captured.meta.tagFirstRep.code)
  }

  @Test
  fun testExtractAndSaveResourcesWithEditModeShouldSaveQuestionnaireResponse() {
    mockkObject(ResourceMapper)

    coEvery { ResourceMapper.extract(any(), any(), any()) } returns
      Bundle().apply { addEntry().resource = samplePatient() }
    coEvery { fhirEngine.get(ResourceType.Patient, "12345") } returns samplePatient()
    coEvery { defaultRepo.addOrUpdate(resource = any()) } just runs

    val questionnaireResponseSlot = slot<QuestionnaireResponse>()
    val patientSlot = slot<Resource>()
    val questionnaire =
      Questionnaire().apply {
        addUseContext().apply {
          code = Coding().apply { code = "focus" }
          value = CodeableConcept().apply { addCoding().apply { code = "1234567" } }
        }
        addExtension().url = "sdc-questionnaire-itemExtractionContext"
      }

    questionnaireViewModel.extractAndSaveResources(
      context = context,
      resourceId = "12345",
      questionnaireResponse = QuestionnaireResponse(),
      questionnaireType = QuestionnaireType.EDIT,
      questionnaire = questionnaire,
      backReference = intent.getStringExtra(QuestionnaireActivity.QUESTIONNAIRE_BACK_REFERENCE_KEY),
    )

    coVerifyOrder {
      defaultRepo.addOrUpdate(resource = capture(patientSlot))
      defaultRepo.addOrUpdate(resource = capture(questionnaireResponseSlot))
    }

    Assert.assertEquals(
      "12345",
      questionnaireResponseSlot.captured.subject.reference.replace("Patient/", ""),
    )
    Assert.assertEquals("12345", patientSlot.captured.id)

    unmockkObject(ResourceMapper)
  }

  @Test
  fun testLoadPatientShouldReturnPatientResource() {
    val patient =
      Patient().apply {
        name =
          listOf(
            HumanName().apply {
              given = listOf(StringType("John"))
              family = "Doe"
            },
          )
      }

    coEvery { fhirEngine.get(ResourceType.Patient, "1") } returns patient

    runBlocking {
      val loadedPatient = questionnaireViewModel.loadPatient("1")

      Assert.assertEquals(
        patient.name.first().given.first().value,
        loadedPatient?.name?.first()?.given?.first()?.value,
      )
      Assert.assertEquals(patient.name.first().family, loadedPatient?.name?.first()?.family)
    }
  }

  @Test
  fun testLoadRelatedPersonShouldReturnOnlyOneItemList() {
    val relatedPerson =
      RelatedPerson().apply {
        name =
          listOf(
            HumanName().apply {
              given = listOf(StringType("John"))
              family = "Doe"
            },
          )
      }

    coEvery { defaultRepo.loadRelatedPersons("1") } returns listOf(relatedPerson)

    runBlocking {
      val list = questionnaireViewModel.loadRelatedPerson("1")
      Assert.assertEquals(1, list?.size)
      val result = list?.get(0)
      Assert.assertEquals(
        relatedPerson.name.first().given.first().value,
        result?.name?.first()?.given?.first()?.value,
      )
      Assert.assertEquals(relatedPerson.name.first().family, result?.name?.first()?.family)
    }
  }

  @Test
  fun testSaveResourceShouldVerifyResourceSaveMethodCall() {
    coEvery { defaultRepo.save(any()) } returns Unit
    questionnaireViewModel.saveResource(mockk())
    coVerify(exactly = 1) { defaultRepo.save(any()) }
  }

  @Test
  fun testGetPopulationResourcesShouldReturnListOfResources() = runTest {
    coEvery { questionnaireViewModel.loadPatient("2") } returns Patient().apply { id = "2" }
    coEvery { defaultRepo.loadRelatedPersons("2") } returns
      listOf(RelatedPerson().apply { id = "3" })

    coEvery { fhirEngine.search<Resource>(any<Search>()) } answers
      {
        val searchObj = firstArg<Search>()
        when (searchObj.type) {
          ResourceType.Appointment -> listOf()
          ResourceType.CarePlan -> listOf()
          else -> emptyList()
        }
      }

    val intent = Intent()
    intent.putStringArrayListExtra(
      QuestionnaireActivity.QUESTIONNAIRE_POPULATION_RESOURCES,
      arrayListOf(
        "{\"resourceType\":\"Patient\",\"id\":\"1\",\"text\":{\"status\":\"generated\",\"div\":\"\"}}",
        "{\"resourceType\":\"Bundle\",\"id\":\"34\",\"text\":{\"status\":\"generated\",\"div\":\"\"}}",
      ),
    )
    intent.putExtra(QuestionnaireActivity.QUESTIONNAIRE_ARG_PATIENT_KEY, "2")

    val resourceList =
      questionnaireViewModel.getPopulationResources(intent, questionnaireConfig.identifier)
    Assert.assertTrue(resourceList.any { it is Bundle })
    Assert.assertEquals(4, resourceList.size)
  }

  @Test
  fun testGetPopulationResourcesShouldReturnListOfResourcesWithTracingResources() = runTest {
    val patient2 = Patient().apply { id = "2" }
    coEvery { questionnaireViewModel.loadPatient("2") } returns patient2
    coEvery { defaultRepo.loadRelatedPersons("2") } returns
      listOf(RelatedPerson().apply { id = "3" })
    val tracingTask =
      Task().apply {
        id = "task0"
        status = Task.TaskStatus.READY
        executionPeriod = Period().apply { start = Date() }
        `for` = patient2.asReference()
        code =
          CodeableConcept().apply {
            addCoding(
              Coding().apply {
                system = "http://snomed.info/sct"
                code = "225368008"
              },
            )
          }
        meta =
          Meta().apply {
            addTag(
              Coding().apply {
                system = "https://dtree.org"
                code = "home-tracing"
                display = "Home Tracing"
              },
            )
          }
        reasonCode =
          CodeableConcept(
            Coding().apply {
              system = "https://dtree.org"
              code = "miss-routine"
            },
          )
        reasonReference = Reference().apply { reference = "Questionnaire/art-tracing-outcome" }
      }

    coEvery { fhirEngine.search<Resource>(any<Search>()) } answers
      {
        val searchObj = firstArg<Search>()
        when (searchObj.type) {
          ResourceType.Appointment -> listOf()
          ResourceType.CarePlan -> listOf()
          ResourceType.Task ->
            listOf(tracingTask).map { SearchResult(it, included = null, revIncluded = null) }
          ResourceType.List -> listOf()
          else -> emptyList()
        }
      }

    mockkObject(TracingHelpers)
    every { TracingHelpers.requireTracingTasks(any()) } returns true

    val intent = Intent()
    intent.putStringArrayListExtra(
      QuestionnaireActivity.QUESTIONNAIRE_POPULATION_RESOURCES,
      arrayListOf(
        "{\"resourceType\":\"Patient\",\"id\":\"1\",\"text\":{\"status\":\"generated\",\"div\":\"\"}}",
        "{\"resourceType\":\"Bundle\",\"id\":\"34\",\"text\":{\"status\":\"generated\",\"div\":\"\"}}",
      ),
    )
    intent.putExtra(QuestionnaireActivity.QUESTIONNAIRE_ARG_PATIENT_KEY, "2")

    val resourceList =
      questionnaireViewModel.getPopulationResources(intent, questionnaireConfig.identifier)
    Assert.assertNotNull(resourceList.firstOrNull { it is Bundle })
    val resourcesListBundle = resourceList.first { it is Bundle } as Bundle
    val tracingBundle = resourcesListBundle.entry.find { it.id == TracingHelpers.tracingBundleId }
    Assert.assertNotNull(tracingBundle)
    tracingBundle!!
    with(tracingBundle) {
      Assert.assertEquals(ResourceType.Bundle, resource.resourceType)
      Assert.assertEquals(TracingHelpers.tracingBundleId, resource.id)
      Assert.assertTrue((resource as Bundle).entry.map { it.resource }.contains(tracingTask))
    }

    unmockkObject(TracingHelpers)
  }

  @Test
  fun testSaveQuestionnaireResponseShouldCallAddOrUpdateWhenResourceIdIsNotBlank() {
    val questionnaire = Questionnaire().apply { id = "qId" }
    val questionnaireResponse = QuestionnaireResponse().apply { subject = Reference("12345") }
    coEvery { defaultRepo.addOrUpdate(resource = any()) } returns Unit

    runBlocking {
      questionnaireViewModel.saveQuestionnaireResponse(questionnaire, questionnaireResponse)
    }

    coVerify { defaultRepo.addOrUpdate(resource = questionnaireResponse) }
  }

  @Test
  fun testSaveQuestionnaireResponseWithExperimentalQuestionnaireShouldNotSave() {
    val questionnaire = Questionnaire().apply { experimental = true }
    val questionnaireResponse = QuestionnaireResponse()

    runBlocking {
      questionnaireViewModel.saveQuestionnaireResponse(questionnaire, questionnaireResponse)
    }

    coVerify(inverse = true) { defaultRepo.addOrUpdate(resource = questionnaireResponse) }
  }

  @Test
  fun testExtractAndSaveResourcesWithExperimentalQuestionnaireShouldNotSave() {
    mockkObject(ResourceMapper)

    coEvery { ResourceMapper.extract(any(), any(), any()) } returns
      Bundle().apply { addEntry().apply { resource = Patient() } }

    val questionnaire =
      Questionnaire().apply {
        experimental = true
        addExtension().url = "sdc-questionnaire-itemExtractionContext"
      }
    val questionnaireResponse = QuestionnaireResponse()

    runBlocking {
      questionnaireViewModel.extractAndSaveResources(
        context = ApplicationProvider.getApplicationContext(),
        resourceId = null,
        questionnaireResponse = questionnaireResponse,
        questionnaire = questionnaire,
        backReference =
          intent.getStringExtra(QuestionnaireActivity.QUESTIONNAIRE_BACK_REFERENCE_KEY),
      )
    }

    coVerify { ResourceMapper.extract(any(), any(), any()) }
    coVerify(inverse = true) { defaultRepo.addOrUpdate(resource = questionnaireResponse) }

    unmockkObject(ResourceMapper)
  }

  @Test
  fun testSaveQuestionnaireResponseShouldAddIdAndAuthoredWhenQuestionnaireResponseDoesNotHaveId() {
    val questionnaire = Questionnaire().apply { id = "qId" }
    val questionnaireResponse = QuestionnaireResponse().apply { subject = Reference("12345") }
    coEvery { defaultRepo.addOrUpdate(resource = any()) } returns Unit

    Assert.assertNull(questionnaireResponse.id)
    Assert.assertNull(questionnaireResponse.authored)

    runBlocking {
      questionnaireViewModel.saveQuestionnaireResponse(questionnaire, questionnaireResponse)
    }

    Assert.assertNotNull(questionnaireResponse.id)
    Assert.assertNotNull(questionnaireResponse.authored)
  }

  @Test
  fun testSaveQuestionnaireResponseShouldRetainIdAndAuthoredWhenQuestionnaireResponseHasId() {
    val authoredDate = Date()
    val questionnaire = Questionnaire().apply { id = "qId" }
    val questionnaireResponse =
      QuestionnaireResponse().apply {
        id = "qrId"
        authored = authoredDate
        subject = Reference("12345")
      }
    coEvery { defaultRepo.addOrUpdate(resource = any()) } returns Unit

    runBlocking {
      questionnaireViewModel.saveQuestionnaireResponse(questionnaire, questionnaireResponse)
    }

    Assert.assertEquals("qrId", questionnaireResponse.id)
    Assert.assertEquals(authoredDate, questionnaireResponse.authored)
  }

  @Test
  fun testExtractAndSaveResourcesShouldCallDeleteRelatedResourcesWhenEditModeIsTrue() {
    mockkObject(ResourceMapper)
    val id = "qrId"
    val authoredDate = Date()
    val versionId = "5"
    val author = Reference()
    val patient = samplePatient()

    coEvery { fhirEngine.get(ResourceType.Patient, any()) } returns Patient()
    coEvery { fhirEngine.get(ResourceType.StructureMap, any()) } returns StructureMap()
    coEvery { ResourceMapper.extract(any(), any(), any()) } returns
      Bundle().apply { addEntry().apply { this.resource = patient } }

    val questionnaire =
      spyk(
        Questionnaire().apply {
          addUseContext().apply {
            code = Coding().apply { code = "focus" }
            value = CodeableConcept().apply { addCoding().apply { code = "1234567" } }
          }
          addExtension(
            "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-targetStructureMap",
            CanonicalType("1234"),
          )
        },
      )

    val oldQuestionnaireResponse =
      QuestionnaireResponse().apply {
        setId(id)
        authored = authoredDate
        setAuthor(author)
        meta.apply { setVersionId(versionId) }
      }
    val questionnaireResponse = spyk(QuestionnaireResponse())

    questionnaireViewModel.editQuestionnaireResponse = oldQuestionnaireResponse
    questionnaireViewModel.extractAndSaveResources(
      context,
      resourceId = "12345",
      questionnaireResponse = questionnaireResponse,
      questionnaireType = QuestionnaireType.EDIT,
      questionnaire = questionnaire,
      backReference = intent.getStringExtra(QuestionnaireActivity.QUESTIONNAIRE_BACK_REFERENCE_KEY),
    )

    verify { questionnaireResponse.retainMetadata(oldQuestionnaireResponse) }

    unmockkObject(ResourceMapper)
  }

  @Test
  fun testCalculateDobFromAge() {
    val expectedBirthDate = Calendar.getInstance()
    val ageInput = expectedBirthDate.get(Calendar.YEAR) - 2010
    expectedBirthDate.set(Calendar.YEAR, 2010)
    expectedBirthDate.set(Calendar.MONTH, 1)
    expectedBirthDate.set(Calendar.DAY_OF_YEAR, 1)
    val resultBirthDate = Calendar.getInstance()
    resultBirthDate.time = questionnaireViewModel.calculateDobFromAge(ageInput)
    Assert.assertEquals(expectedBirthDate.get(Calendar.YEAR), resultBirthDate.get(Calendar.YEAR))
  }

  @Test
  fun testGetAgeInputFromQuestionnaire() {
    val expectedAge = 25
    val questionnaireResponse =
      QuestionnaireResponse().apply {
        id = "12345"
        item =
          listOf(
            QuestionnaireResponse.QuestionnaireResponseItemComponent().apply {
              linkId = "q1-grp"
              item =
                listOf(
                  QuestionnaireResponse.QuestionnaireResponseItemComponent().apply {
                    linkId = QuestionnaireActivity.QUESTIONNAIRE_AGE
                    answer =
                      listOf(
                        QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent().apply {
                          value = DecimalType(25)
                        },
                      )
                  },
                )
            },
          )
      }
    Assert.assertEquals(expectedAge, questionnaireViewModel.getAgeInput(questionnaireResponse))
  }

  @Test
  fun `saveBundleResources() should call saveResources()`() {
    val bundle = Bundle()
    val size = 1

    for (i in 1..size) {
      val bundleEntry = Bundle.BundleEntryComponent()
      bundleEntry.resource =
        Patient().apply {
          name =
            listOf(
              HumanName().apply {
                family = "Doe"
                given = listOf(StringType("John"))
              },
            )
        }
      bundle.addEntry(bundleEntry)
    }
    bundle.total = size

    // call the method under test
    runBlocking { questionnaireViewModel.saveBundleResources(bundle) }

    coVerify(exactly = size) { defaultRepo.addOrUpdate(resource = any()) }
  }

  @Test
  fun `saveBundleResources() should call saveResources and inject resourceId()`() {
    val bundle = Bundle()
    val size = 5
    val resource = slot<Resource>()

    val bundleEntry = Bundle.BundleEntryComponent()
    bundleEntry.resource =
      Patient().apply {
        name =
          listOf(
            HumanName().apply {
              family = "Doe"
              given = listOf(StringType("John"))
            },
          )
      }
    bundle.addEntry(bundleEntry)
    bundle.total = size

    // call the method under test
    runBlocking { questionnaireViewModel.saveBundleResources(bundle) }

    coVerify(exactly = 1) { defaultRepo.addOrUpdate(resource = capture(resource)) }
  }

  @Test
  fun `fetchStructureMap() should call fhirEngine load and parse out the resourceId`() {
    val structureMap = StructureMap()
    val structureMapIdSlot = slot<String>()

    coEvery { fhirEngine.get(ResourceType.StructureMap, any()) } returns structureMap

    runBlocking {
      questionnaireViewModel.fetchStructureMap("https://someorg.org/StructureMap/678934")
    }

    coVerify(exactly = 1) { fhirEngine.get(ResourceType.StructureMap, capture(structureMapIdSlot)) }

    Assert.assertEquals("678934", structureMapIdSlot.captured)
  }

  @Test
  fun `extractAndSaveResources() should call saveBundleResources when Questionnaire uses Definition-based extraction`() {
    coEvery { fhirEngine.get(ResourceType.Questionnaire, any()) } returns
      samplePatientRegisterQuestionnaire
    coEvery { fhirEngine.get(ResourceType.Group, any()) } returns Group()

    val questionnaire = Questionnaire()
    questionnaire.extension.add(
      Extension(
        "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-itemExtractionContext",
        Expression().apply {
          language = "application/x-fhir-query"
          expression = "Patient"
        },
      ),
    )
    questionnaire.addSubjectType("Patient")
    val questionnaireResponse = QuestionnaireResponse()

    coEvery { questionnaireViewModel.saveBundleResources(any()) } just runs
    coEvery { questionnaireViewModel.performExtraction(any(), any(), any()) } returns
      Bundle().apply { addEntry().resource = samplePatient() }

    coEvery { questionnaireViewModel.saveQuestionnaireResponse(any(), any()) } just runs

    questionnaireViewModel.extractAndSaveResources(
      context = context,
      resourceId = "0993ldsfkaljlsnldm",
      questionnaireResponse = questionnaireResponse,
      questionnaire = questionnaire,
      backReference = intent.getStringExtra(QuestionnaireActivity.QUESTIONNAIRE_BACK_REFERENCE_KEY),
    )

    coVerify(exactly = 1, timeout = 2000) { questionnaireViewModel.saveBundleResources(any()) }
    coVerify(exactly = 1, timeout = 2000) {
      questionnaireViewModel.saveQuestionnaireResponse(questionnaire, questionnaireResponse)
    }
  }

  @Test
  fun `extractAndSaveResources() should call runCqlFor when Questionnaire uses cqf-library extenion`() {
    coEvery { fhirEngine.get(ResourceType.Questionnaire, any()) } returns
      samplePatientRegisterQuestionnaire
    coEvery { fhirEngine.get(ResourceType.Group, any()) } returns Group()

    val questionnaire = Questionnaire()
    questionnaire.extension.add(
      Extension(
        "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-itemExtractionContext",
        Expression().apply {
          language = "application/x-fhir-query"
          expression = "Patient"
        },
      ),
    )
    questionnaire.extension.add(
      Extension(
        "http://hl7.org/fhir/uv/sdc/StructureDefinition/cqf-library",
        CanonicalType("Library/123"),
      ),
    )

    questionnaire.addSubjectType("Patient")
    val questionnaireResponse = QuestionnaireResponse()

    coEvery { questionnaireViewModel.loadPatient(any()) } returns samplePatient()
    coEvery { questionnaireViewModel.saveBundleResources(any()) } just runs
    coEvery { questionnaireViewModel.performExtraction(any(), any(), any()) } returns
      Bundle().apply { addEntry().resource = samplePatient() }

    coEvery { questionnaireViewModel.saveQuestionnaireResponse(any(), any()) } just runs

    questionnaireViewModel.extractAndSaveResources(
      context = context,
      resourceId = "0993ldsfkaljlsnldm",
      questionnaireResponse = questionnaireResponse,
      questionnaire = questionnaire,
      backReference = intent.getStringExtra(QuestionnaireActivity.QUESTIONNAIRE_BACK_REFERENCE_KEY),
    )

    coVerify(exactly = 1, timeout = 2000) { questionnaireViewModel.saveBundleResources(any()) }
    coVerify(exactly = 1, timeout = 2000) {
      questionnaireViewModel.saveQuestionnaireResponse(questionnaire, questionnaireResponse)
    }
  }

  @Test
  fun testSaveResourceShouldCallDefaultRepositorySave() {
    val sourcePatient = Patient().apply { id = "test_patient_1_id" }
    questionnaireViewModel.saveResource(sourcePatient)

    coVerify { defaultRepo.save(sourcePatient) }
  }

  @Test
  fun `getStructureMapProvider() should return valid provider`() {
    Assert.assertNull(questionnaireViewModel.structureMapProvider)

    Assert.assertNotNull(questionnaireViewModel.retrieveStructureMapProvider())
  }

  @Test
  fun `structureMapProvider should call fetchStructureMap()`() {
    val resourceUrl = "https://fhir.org/StructureMap/89"
    val structureMapProvider = questionnaireViewModel.retrieveStructureMapProvider()

    coEvery { questionnaireViewModel.fetchStructureMap(any()) } returns StructureMap()

    runBlocking { structureMapProvider.invoke(resourceUrl, SimpleWorkerContext()) }

    coVerify { questionnaireViewModel.fetchStructureMap(resourceUrl) }
  }

  fun testHandlePatientSubjectShouldReturnSetCorrectReference() {
    val questionnaire = Questionnaire().apply { addSubjectType("Patient") }
    val questionnaireResponse = QuestionnaireResponse()

    Assert.assertFalse(questionnaireResponse.hasSubject())

    questionnaireViewModel.handleQuestionnaireResponseSubject(
      "123",
      questionnaire,
      questionnaireResponse,
    )

    Assert.assertEquals("Patient/123", questionnaireResponse.subject.reference)
  }

  @Test
  fun testHandleOrganizationSubjectShouldReturnSetCorrectReference() {
    val questionnaire = Questionnaire().apply { addSubjectType("Organization") }
    val questionnaireResponse = QuestionnaireResponse()

    Assert.assertFalse(questionnaireResponse.hasSubject())

    questionnaireViewModel.handleQuestionnaireResponseSubject(
      "123",
      questionnaire,
      questionnaireResponse,
    )

    Assert.assertEquals("Organization/1111", questionnaireResponse.subject.reference)
  }

  private fun getUserInfo(): UserInfo {
    val userInfo =
      UserInfo().apply {
        questionnairePublisher = "ab"
        organization = "1111"
        keycloakUuid = "123"
      }
    return userInfo
  }

  private fun samplePatient() =
    Patient().apply {
      Patient@ this.id = "123456"
      this.birthDate = questionnaireViewModel.calculateDobFromAge(25)
    }

  private fun practitionerDetails(): PractitionerDetails {
    return PractitionerDetails().apply {
      fhirPractitionerDetails =
        FhirPractitionerDetails().apply {
          id = "12345"
          practitioners?.firstOrNull()?.id = StringType("12345").toString()
        }
    }
  }

  @Test
  fun testAddPractitionerInfoShouldSetGeneralPractitionerReferenceToPatientResource() {
    val patient = samplePatient()

    questionnaireViewModel.appendPractitionerInfo(patient)

    Assert.assertEquals(null, patient.generalPractitioner.firstOrNull())
  }

  @Test
  fun testAddOrganizationInfoShouldSetOrganizationToQuestionnaireResponse() {
    // For patient
    val patient = samplePatient()
    questionnaireViewModel.appendOrganizationInfo(patient)
    Assert.assertNotNull("Organization/1111", patient.managingOrganization.reference)

    // For group
    val group = Group().apply { id = "123" }
    questionnaireViewModel.appendOrganizationInfo(group)
    Assert.assertEquals("Organization/1111", group.managingEntity.reference)
  }

  @Test
  fun testAddPractitionerInfoShouldSetIndividualPractitionerReferenceToEncounterResource() {
    val encounter = Encounter().apply { this.id = "123456" }
    questionnaireViewModel.appendPractitionerInfo(encounter)
    //    Assert.assertEquals("Practitioner/12345", encounter.participant[0].individual.reference)
    Assert.assertEquals(null, encounter.participant.firstOrNull()?.individual?.reference)
  }

  @Test
  fun testAppendPatientsAndRelatedPersonsToGroupsShouldAddMembersToGroup() = runTest {
    val patient = samplePatient()
    val familyGroup =
      Group().apply {
        id = "grp1"
        name = "Mandela Family"
      }
    coEvery { fhirEngine.get<Group>(familyGroup.id) } returns familyGroup
    questionnaireViewModel.appendPatientsAndRelatedPersonsToGroups(patient, familyGroup.id)
    Assert.assertEquals(1, familyGroup.member.size)

    val familyGroup2 = Group().apply { id = "grp2" }
    coEvery { fhirEngine.get<Group>(familyGroup2.id) } returns familyGroup2
    // Sets the managing entity
    questionnaireViewModel.appendPatientsAndRelatedPersonsToGroups(
      RelatedPerson().apply { id = "rel1" },
      familyGroup2.id,
    )
    Assert.assertNotNull(familyGroup2.managingEntity)
  }

  @Test
  fun `test carePlanAndPatientMetaExtraction`() = runTest {
    val carePlan =
      CarePlan().copy().apply {
        id = "12345"
        meta.versionId = 1.toString()
      }
    val tags = emptyList<Coding>()
    carePlan.addTags(tags)

    coEvery { fhirEngine.create(carePlan) } answers { listOf(carePlan.id) }
    coEvery { fhirEngine.get<CarePlan>("12345") } returns carePlan

    questionnaireViewModel.carePlanAndPatientMetaExtraction(carePlan)

    val resource = fhirEngine.get(carePlan.resourceType, carePlan.id) as CarePlan

    Assert.assertTrue(resource.meta.tag.isEmpty())
    Assert.assertEquals(1.toString(), resource.meta.versionId)

    Assert.assertNull(resource.status)
  }

  @Test
  fun `test generateQuestionnaireResponse`() = runTest {
    val questionnaire = Questionnaire().apply { id = "test" }
    val patient = samplePatient()
    coEvery {
      questionnaireViewModel.getPopulationResources(any(), questionnaire.logicalId)
    } returns arrayOf(patient)
    val intent = Intent()

    val response = questionnaireViewModel.generateQuestionnaireResponse(questionnaire, intent)

    Assert.assertNotNull(response.contained.firstOrNull { it.resourceType == ResourceType.Patient })
    Assert.assertEquals(response.questionnaire, "${questionnaire.resourceType}/test")
  }

  @Test
  fun `test extractRelevantObservation`() = runTest {
    val questionnaire = Questionnaire()
    val bundle = Bundle()
    val response =
      questionnaireViewModel.extractRelevantObservation(bundle, questionnaire.logicalId)
    Assert.assertArrayEquals(bundle.entry.toTypedArray(), response.entry.toTypedArray())
    Assert.assertTrue(response.entry.isEmpty())
  }

  @Test
  fun `test extractRelevantObservation with Observation resource`() = runTest {
    val questionnaire = Questionnaire().apply { id = "1234" }
    val bundle = Bundle()
    val observation =
      Observation().apply {
        code = CodeableConcept().apply { addCoding(Coding().apply { code = "Observation-1234" }) }
      }
    bundle.entry.add(Bundle.BundleEntryComponent().setResource(observation))
    val response =
      questionnaireViewModel.extractRelevantObservation(bundle, questionnaire.logicalId)
    Assert.assertNotEquals(bundle.entry.toTypedArray(), response.entry.toTypedArray())
    Assert.assertEquals(response.entry.size, 1)
  }
}
