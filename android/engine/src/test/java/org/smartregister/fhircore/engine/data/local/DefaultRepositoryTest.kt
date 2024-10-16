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

package org.smartregister.fhircore.engine.data.local

import com.google.android.fhir.FhirEngine
import com.google.android.fhir.SearchResult
import com.google.android.fhir.datacapture.extensions.logicalId
import com.google.android.fhir.db.ResourceNotFoundException
import com.google.android.fhir.get
import com.google.android.fhir.search.Search
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.runTest
import org.hl7.fhir.r4.model.Address
import org.hl7.fhir.r4.model.Binary
import org.hl7.fhir.r4.model.Composition
import org.hl7.fhir.r4.model.ContactPoint
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.HumanName
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.StringType
import org.joda.time.LocalDate
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.smartregister.fhircore.engine.app.fakes.Faker
import org.smartregister.fhircore.engine.configuration.app.ConfigService
import org.smartregister.fhircore.engine.robolectric.RobolectricTest
import org.smartregister.fhircore.engine.rule.CoroutineTestRule
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import org.smartregister.fhircore.engine.util.extension.generateMissingId
import org.smartregister.fhircore.engine.util.extension.generateMissingVersionId
import org.smartregister.fhircore.engine.util.extension.loadPatientImmunizations
import org.smartregister.fhircore.engine.util.extension.loadRelatedPersons

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class DefaultRepositoryTest : RobolectricTest() {

  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @OptIn(ExperimentalCoroutinesApi::class)
  @get:Rule(order = 2)
  var coroutineRule = CoroutineTestRule()
  private val configurationRegistry = Faker.buildTestConfigurationRegistry()

  @Inject lateinit var dispatcherProvider: DispatcherProvider

  @BindValue val sharedPreferencesHelper = mockk<SharedPreferencesHelper>(relaxed = true)

  private val configService: ConfigService = mockk()

  @Before
  fun setUp() {
    hiltRule.inject()
    every { configService.provideResourceTags(any()) } returns listOf()
  }

  @Test
  fun `addOrUpdate() should call fhirEngine#update when resource exists`() {
    val patientId = "15672-9234"
    val patient: Patient =
      Patient().apply {
        id = patientId
        active = true
        birthDate = LocalDate.parse("1996-08-17").toDate()
        gender = Enumerations.AdministrativeGender.MALE
        address =
          listOf(
            Address().apply {
              city = "Lahore"
              country = "Pakistan"
            },
          )
        name =
          listOf(
            HumanName().apply {
              given = mutableListOf(StringType("Salman"))
              family = "Ali"
            },
          )
        telecom = listOf(ContactPoint().apply { value = "12345" })
      }

    val savedPatientSlot = slot<Patient>()

    val fhirEngine: FhirEngine = mockk()
    coEvery { fhirEngine.get(any(), any()) } answers { patient }
    coEvery { fhirEngine.update(any()) } just runs

    val defaultRepository =
      DefaultRepository(
        fhirEngine = fhirEngine,
        dispatcherProvider = dispatcherProvider,
        sharedPreferencesHelper = sharedPreferencesHelper,
        configurationRegistry = configurationRegistry,
        configService = configService,
      )

    // Call the function under test
    runBlocking { defaultRepository.addOrUpdate(resource = patient) }

    coVerify { fhirEngine.get(ResourceType.Patient, patientId) }
    coVerify { fhirEngine.update(capture(savedPatientSlot)) }

    savedPatientSlot.captured.run {
      Assert.assertEquals(patient.idElement.idPart, idElement.idPart)
      Assert.assertEquals(patient.active, active)
      Assert.assertEquals(patient.birthDate, birthDate)
      Assert.assertEquals(patient.telecom[0].value, telecom[0].value)
      Assert.assertEquals(patient.name[0].family, name[0].family)
      Assert.assertEquals(patient.name[0].given[0].value, name[0].given[0].value)
      Assert.assertEquals(patient.address[0].city, address[0].city)
      Assert.assertEquals(patient.address[0].country, address[0].country)
    }

    // verify exception scenario
    coEvery { fhirEngine.get(ResourceType.Patient, any()) } throws
      mockk<ResourceNotFoundException>()
    coEvery { fhirEngine.create(any()) } returns listOf()
    runBlocking { defaultRepository.addOrUpdate(resource = Patient()) }
    coVerify(exactly = 1) { fhirEngine.create(any()) }
  }

  @Test
  fun `loadRelatedPersons() should call FhirEngine#loadRelatedPersons`() {
    val patientId = "15672-9234"
    val fhirEngine: FhirEngine = mockk<FhirEngine>()
    mockkStatic(FhirEngine::loadRelatedPersons)
    coEvery { fhirEngine.loadRelatedPersons(patientId) } returns listOf()

    val defaultRepository =
      DefaultRepository(
        fhirEngine = fhirEngine,
        dispatcherProvider = dispatcherProvider,
        sharedPreferencesHelper = sharedPreferencesHelper,
        configurationRegistry = configurationRegistry,
        configService = configService,
      )

    runBlocking { defaultRepository.loadRelatedPersons(patientId) }

    coVerify { fhirEngine.loadRelatedPersons(patientId) }
    unmockkStatic(FhirEngine::loadRelatedPersons)
  }

  @Test
  fun `loadImmunizations() should call FhirEngine#loadImmunizations`() {
    val patientId = "15672-9234"
    val fhirEngine: FhirEngine = mockk()
    mockkStatic(FhirEngine::loadPatientImmunizations)
    coEvery { fhirEngine.loadPatientImmunizations(patientId) } returns listOf()

    val defaultRepository =
      DefaultRepository(
        fhirEngine = fhirEngine,
        dispatcherProvider = dispatcherProvider,
        sharedPreferencesHelper = sharedPreferencesHelper,
        configurationRegistry = configurationRegistry,
        configService = configService,
      )

    runBlocking { defaultRepository.loadPatientImmunizations(patientId) }

    coVerify { fhirEngine.loadPatientImmunizations(patientId) }
    mockkStatic(FhirEngine::loadPatientImmunizations)
  }

  @Test
  fun `loadQuestionnaireResponse() should call FhirEngine#load`() {
    val fhirEngine: FhirEngine = mockk()
    coEvery { fhirEngine.search<QuestionnaireResponse>(any<Search>()) } returns listOf()

    val defaultRepository =
      DefaultRepository(
        fhirEngine = fhirEngine,
        dispatcherProvider = dispatcherProvider,
        sharedPreferencesHelper = sharedPreferencesHelper,
        configurationRegistry = configurationRegistry,
        configService = configService,
      )

    runBlocking { defaultRepository.loadQuestionnaireResponses("1234", Questionnaire()) }

    coVerify { fhirEngine.search<QuestionnaireResponse>(any<Search>()) }
  }

  @Test
  fun `save() should call Resource#generateMissingId()`() {
    mockkStatic(Resource::generateMissingId)
    val resource = spyk(Patient())

    val fhirEngine: FhirEngine = mockk()
    coEvery { fhirEngine.get(ResourceType.Patient, any()) } throws
      ResourceNotFoundException("Exce", "Exce")
    coEvery { fhirEngine.create(any()) } returns listOf()
    val defaultRepository =
      DefaultRepository(
        fhirEngine = fhirEngine,
        dispatcherProvider = dispatcherProvider,
        sharedPreferencesHelper = sharedPreferencesHelper,
        configurationRegistry = configurationRegistry,
        configService = configService,
      )

    runBlocking { defaultRepository.save(resource) }

    verify { resource.generateMissingId() }

    unmockkStatic(Resource::generateMissingId)
  }

  @Test
  fun `addOrUpdate() should call Resource#generateMissingId() when ResourceId is null`() {
    mockkStatic(Resource::generateMissingId)
    val resource = Patient()

    val fhirEngine: FhirEngine = mockk()
    coEvery { fhirEngine.get(ResourceType.Patient, any()) } throws
      ResourceNotFoundException("Exce", "Exce")
    coEvery { fhirEngine.create(any()) } returns listOf()

    val defaultRepository =
      DefaultRepository(
        fhirEngine = fhirEngine,
        dispatcherProvider = dispatcherProvider,
        sharedPreferencesHelper = sharedPreferencesHelper,
        configurationRegistry = configurationRegistry,
        configService = configService,
      )

    runBlocking { defaultRepository.addOrUpdate(resource = resource) }

    verify { resource.generateMissingId() }

    unmockkStatic(Resource::generateMissingId)
  }

  @Test
  fun testSearchCompositionByIdentifier() = runBlockingTest {
    val fhirEngine: FhirEngine = mockk()
    coEvery { fhirEngine.search<Composition>(any<Search>()) } returns
      listOf(SearchResult(Composition().apply { id = "123" }, included = null, revIncluded = null))

    val defaultRepository =
      DefaultRepository(
        fhirEngine = fhirEngine,
        dispatcherProvider = dispatcherProvider,
        sharedPreferencesHelper = sharedPreferencesHelper,
        configurationRegistry = configurationRegistry,
        configService = configService,
      )

    val result = defaultRepository.searchCompositionByIdentifier("appId")

    coVerify { fhirEngine.search<Composition>(any<Search>()) }

    Assert.assertEquals("123", result!!.logicalId)
  }

  @Test
  fun testGetBinaryResource() = runBlockingTest {
    val fhirEngine: FhirEngine = mockk()
    coEvery { fhirEngine.get(ResourceType.Binary, any()) } returns Binary().apply { id = "111" }

    val defaultRepository =
      DefaultRepository(
        fhirEngine = fhirEngine,
        dispatcherProvider = dispatcherProvider,
        sharedPreferencesHelper = sharedPreferencesHelper,
        configurationRegistry = configurationRegistry,
        configService = configService,
      )

    val result = defaultRepository.getBinary("111")

    coVerify { fhirEngine.get(ResourceType.Binary, any()) }

    Assert.assertEquals("111", result.logicalId)
  }

  @Test
  fun `addOrUpdate() should call Resource#generateMissingVersionId() when versionId is null`() {
    mockkStatic(Resource::generateMissingVersionId)
    val resource = Patient()

    val fhirEngine: FhirEngine = mockk()
    coEvery { fhirEngine.get(ResourceType.Patient, any()) } throws
      ResourceNotFoundException("Exce", "Exce")
    coEvery { fhirEngine.create(any()) } returns listOf()
    val defaultRepository =
      DefaultRepository(
        fhirEngine = fhirEngine,
        dispatcherProvider = dispatcherProvider,
        sharedPreferencesHelper = sharedPreferencesHelper,
        configurationRegistry = configurationRegistry,
        configService = configService,
      )

    runBlocking { defaultRepository.addOrUpdate(resource = resource) }

    verify { resource.generateMissingVersionId() }

    unmockkStatic(Resource::generateMissingVersionId)
  }

  @Test
  fun `searchQuestionnaireConfig should call FhirEngine#Search and return QuestionnaireConfig with correct fields`() =
    runTest {
      val fhirEngine = mockk<FhirEngine>()
      val questionnaire1 =
        Questionnaire().apply {
          id = "1"
          name = "questionnaire1"
          title = "Questionnaire 1"
        }
      val questionnaire2 =
        Questionnaire().apply {
          id = "2"
          name = "questionnaire2"
          title = "Questionnaire 2"
        }

      coEvery { fhirEngine.search<Resource>(any<Search>()) } answers
        {
          val searchArg = firstArg<Search>()
          when (searchArg.type) {
            ResourceType.Questionnaire ->
              listOf(questionnaire1, questionnaire2).map {
                SearchResult(it, included = null, revIncluded = null)
              }
            else -> emptyList()
          }
        }

      val defaultRepository =
        DefaultRepository(
          fhirEngine = fhirEngine,
          dispatcherProvider = dispatcherProvider,
          sharedPreferencesHelper = sharedPreferencesHelper,
          configurationRegistry = configurationRegistry,
          configService = configService,
        )
      val questionnaireConfigs = defaultRepository.searchQuestionnaireConfig(listOf())
      Assert.assertEquals(2, questionnaireConfigs.size)
      val questionnaireConfig1 = questionnaireConfigs[0]
      val questionnaireConfig2 = questionnaireConfigs[1]
      with(questionnaireConfig1) {
        Assert.assertEquals("questionnaire1", form)
        Assert.assertEquals("Questionnaire 1", title)
        Assert.assertEquals("1", identifier)
      }

      with(questionnaireConfig2) {
        Assert.assertEquals("questionnaire2", form)
        Assert.assertEquals("Questionnaire 2", title)
        Assert.assertEquals("2", identifier)
      }
    }
}
