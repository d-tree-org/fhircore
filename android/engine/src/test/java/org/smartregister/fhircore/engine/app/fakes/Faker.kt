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

package org.smartregister.fhircore.engine.app.fakes

import androidx.test.core.app.ApplicationProvider
import com.google.android.fhir.FhirEngine
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import java.io.File
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.runBlocking
import org.hl7.fhir.r4.model.Binary
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.DateType
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.StringType
import org.smartregister.fhircore.engine.app.TestAppConfigService
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.data.local.register.dao.HivRegisterDao.Companion.ORGANISATION_DISPLAY
import org.smartregister.fhircore.engine.data.local.register.dao.HivRegisterDao.Companion.ORGANISATION_SYSTEM
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceDataSource
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceService
import org.smartregister.fhircore.engine.robolectric.RobolectricTest.Companion.readFile

object Faker {
  private const val APP_DEBUG = "default/debug"
  private val systemPath =
    (System.getProperty("user.dir") +
      File.separator +
      "src" +
      File.separator +
      "main" +
      File.separator +
      "assets" +
      File.separator)

  fun loadTestConfigurationRegistryData(
    fhirEngine: FhirEngine,
    configurationRegistry: ConfigurationRegistry,
  ) {
    coEvery { fhirEngine.get(ResourceType.Binary, any()) } answers
      {
        val binaryResId = secondArg<String>()
        Binary().apply { content = getBasePath(binaryResId).readFile(systemPath).toByteArray() }
      }
    runBlocking { configurationRegistry.loadConfigurations() }
  }

  private fun getBasePath(id: String): String {
    return "/configs/config_$id.json"
  }

  fun appConfigService() = TestAppConfigService()

  fun buildTestConfigurationRegistry(): ConfigurationRegistry {
    val fhirResourceService = mockk<FhirResourceService>()
    val fhirResourceDataSource = spyk(FhirResourceDataSource(fhirResourceService))
    coEvery { fhirResourceService.getResource(any()) } returns Bundle()
    val fhirEngine: FhirEngine = mockk()

    coEvery { fhirEngine.get(ResourceType.Binary, any()) } answers
      {
        val binaryResId = secondArg<String>()
        Binary().apply { content = getBasePath(binaryResId).readFile(systemPath).toByteArray() }
      }

    val configurationRegistry =
      spyk(
        ConfigurationRegistry(
          fhirEngine = fhirEngine,
          fhirResourceDataSource = fhirResourceDataSource,
          sharedPreferencesHelper = mockk(),
          dispatcherProvider = mockk(),
          context = ApplicationProvider.getApplicationContext(),
          appConfigService = appConfigService(),
        ),
      )

    runBlocking { configurationRegistry.loadConfigurations() }

    return configurationRegistry
  }

  fun buildPatient(
    id: String = "sampleId",
    family: String = "Mandela",
    given: String = "Nelson",
    age: Int = 78,
    gender: Enumerations.AdministrativeGender? = Enumerations.AdministrativeGender.MALE,
    patientType: String = "",
    practitionerReference: String = "",
    deceased: Boolean = false,
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

      this.meta.addTag(
        Coding().apply {
          system = "https://d-tree.org"
          code = patientType
          display = "Exposed Infant"
        },
      )

      this.meta.addTag(
        Coding().apply {
          system = ORGANISATION_SYSTEM
          code = "123"
          display = ORGANISATION_DISPLAY
        },
      )

      this.generalPractitionerFirstRep.apply { reference = practitionerReference }
      this.deceased = deceasedBooleanType
    }
  }
}
