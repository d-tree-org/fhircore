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

package org.smartregister.fhircore.quest.app.fakes

import com.google.android.fhir.FhirEngine
import com.google.android.fhir.SearchResult
import com.google.android.fhir.search.Search
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.runBlocking
import org.hl7.fhir.r4.model.Address
import org.hl7.fhir.r4.model.Binary
import org.hl7.fhir.r4.model.Composition
import org.hl7.fhir.r4.model.DateType
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.HumanName
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.StringType
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireConfig
import org.smartregister.fhircore.engine.util.extension.decodeResourceFromString
import org.smartregister.fhircore.quest.data.patient.PatientRepository
import org.smartregister.fhircore.quest.data.patient.model.PatientItem
import org.smartregister.fhircore.quest.robolectric.RobolectricTest.Companion.readFile

object Faker {
  private const val APP_DEBUG = "quest"

  fun buildPatient(
    id: String = "sampleId",
    family: String = "Mandela",
    given: String = "Nelson",
    age: Int = 78,
    gender: Enumerations.AdministrativeGender = Enumerations.AdministrativeGender.MALE,
  ): Patient {
    return Patient().apply {
      this.id = id
      this.identifierFirstRep.value = id
      this.addName().apply {
        this.family = family
        this.given.add(StringType(given))
      }
      this.gender = gender
      this.birthDate = DateType(Date()).apply { add(Calendar.YEAR, -age) }.dateTimeValue().value

      this.addAddress().apply {
        district = "Dist 1"
        city = "City 1"
      }
    }
  }

  fun initPatientRepositoryMocks(patientRepository: PatientRepository) {
    coEvery { patientRepository.fetchDemographicsWithAdditionalData(any()) } answers
      {
        PatientItem(id = firstArg(), name = "John Doe", gender = "M", age = "22y")
      }

    coEvery { patientRepository.fetchDemographics(any()) } returns
      Patient().apply {
        name =
          listOf(
            HumanName().apply {
              family = "Doe"
              given = listOf(StringType("John"))
            },
          )
        id = "5583145"
        gender = Enumerations.AdministrativeGender.MALE
        birthDate = SimpleDateFormat("yyyy-MM-dd").parse("2000-01-01")
        address =
          listOf(
            Address().apply {
              city = "Nairobi"
              country = "Kenya"
            },
          )
        identifier = listOf(Identifier().apply { value = "12345" })
      }

    coEvery { patientRepository.fetchTestForms(any()) } returns
      listOf(
        QuestionnaireConfig(
          form = "sample-order-result",
          title = "Sample Order Result",
          identifier = "12345",
        ),
        QuestionnaireConfig(
          form = "sample-test-result",
          title = "Sample Test Result",
          identifier = "67890",
        ),
      )

    coEvery { patientRepository.fetchPregnancyCondition(any()) } returns ""
  }

  fun initPatientRepositoryEmptyMocks(patientRepository: PatientRepository) {
    coEvery { patientRepository.fetchDemographics(any()) } returns Patient()
    coEvery { patientRepository.fetchTestForms(any()) } returns emptyList()
  }

  val systemPath =
    (System.getProperty("user.dir") +
      File.separator +
      "src" +
      File.separator +
      "main" +
      File.separator +
      "assets" +
      File.separator)

  fun loadTestConfigurationRegistryData(
    appId: String,
    fhirEngine: FhirEngine,
    configurationRegistry: ConfigurationRegistry,
  ) {
    val composition =
      getBasePath(appId, "composition").readFile(systemPath).decodeResourceFromString()
        as Composition
    coEvery { fhirEngine.search<Composition>(any<Search>()) } returns
      listOf(SearchResult(composition, included = null, revIncluded = null))

    coEvery { fhirEngine.get(ResourceType.Binary, any()) } answers
      {
        val sectionComponent =
          composition.section.find {
            this.args[1].toString() == it.focus.reference.substringAfter("Binary/")
          }
        val configName = sectionComponent!!.focus.identifier.value
        Binary().apply {
          content = getBasePath(appId, configName).readFile(systemPath).toByteArray()
        }
      }

    runBlocking { configurationRegistry.loadConfigurations(appId) {} }
  }

  private fun getBasePath(appId: String, classification: String): String {
    return "/configs/$appId/config_$classification.json"
  }

  fun buildTestConfigurationRegistry(
    appId: String? = null,
  ): ConfigurationRegistry {
    val fhirEngine: FhirEngine = mockk()
    val configurationRegistry =
      spyk(ConfigurationRegistry(mockk(), fhirEngine, mockk(), mockk(), mockk()))

    loadTestConfigurationRegistryData(appId ?: APP_DEBUG, fhirEngine, configurationRegistry)

    return configurationRegistry
  }
}
