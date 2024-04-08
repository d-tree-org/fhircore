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

package org.smartregister.fhircore.engine.ui.appsetting

import androidx.test.core.app.ApplicationProvider
import com.google.gson.GsonBuilder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Composition
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.smartregister.fhircore.engine.app.fakes.Faker
import org.smartregister.fhircore.engine.configuration.app.ConfigService
import org.smartregister.fhircore.engine.data.local.DefaultRepository
import org.smartregister.fhircore.engine.data.remote.config.ConfigRepository
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceDataSource
import org.smartregister.fhircore.engine.robolectric.RobolectricTest
import org.smartregister.fhircore.engine.rule.CoroutineTestRule
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class AppSettingViewModelTest : RobolectricTest() {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @ExperimentalCoroutinesApi @get:Rule(order = 1) val coroutineTestRule = CoroutineTestRule()
  private val defaultRepository = mockk<DefaultRepository>()
  private val fhirResourceDataSource = mockk<FhirResourceDataSource>()
  private val sharedPreferencesHelper =
    SharedPreferencesHelper(
      ApplicationProvider.getApplicationContext(),
      GsonBuilder().setLenient().create(),
    )

  @Inject lateinit var dispatcherProvider: DispatcherProvider

  private val configService = mockk<ConfigService>()

  @ExperimentalCoroutinesApi private lateinit var appSettingViewModel: AppSettingViewModel

  private val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
  private val configRepository =
    mockk<ConfigRepository>() { coEvery { fetchConfigFromRemote() } just runs }

  @Before
  fun setUp() {
    hiltRule.inject()
    appSettingViewModel =
      spyk(
        AppSettingViewModel(
          fhirResourceDataSource = fhirResourceDataSource,
          defaultRepository = defaultRepository,
          sharedPreferencesHelper = sharedPreferencesHelper,
          configurationRegistry = Faker.buildTestConfigurationRegistry(),
          dispatcherProvider = dispatcherProvider,
          configRepository = configRepository,
        ),
      )
  }

  @Test
  @ExperimentalCoroutinesApi
  fun testLoadConfigurations() = runTest {
    coEvery { fhirResourceDataSource.getResource(any()) } returns
      Bundle().apply { addEntry().resource = Composition() }
    coEvery { appSettingViewModel.defaultRepository.create(any()) } returns emptyList()

    appSettingViewModel.loadConfigurations()
    Assert.assertNotNull(appSettingViewModel.goToHome.value)
    Assert.assertTrue(appSettingViewModel.goToHome.value!!)
  }

  @Test
  fun addTests() {
    TODO("Add new tests")
  }
}
