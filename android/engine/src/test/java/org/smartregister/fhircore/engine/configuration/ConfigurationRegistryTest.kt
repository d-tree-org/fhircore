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

package org.smartregister.fhircore.engine.configuration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.fhir.FhirEngine
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.ResourceType
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.smartregister.fhircore.engine.app.fakes.Faker
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceDataSource
import org.smartregister.fhircore.engine.robolectric.RobolectricTest
import org.smartregister.fhircore.engine.rule.CoroutineTestRule
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper

@HiltAndroidTest
@ExperimentalCoroutinesApi
class ConfigurationRegistryTest : RobolectricTest() {

  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
  val context = ApplicationProvider.getApplicationContext<Context>()

  @get:Rule(order = 1) val coroutineRule = CoroutineTestRule()

  @Inject lateinit var dispatcherProvider: DispatcherProvider
  private val testAppId = "default"
  private lateinit var fhirResourceDataSource: FhirResourceDataSource
  lateinit var configurationRegistry: ConfigurationRegistry
  var fhirEngine: FhirEngine = mockk()

  @Before
  @ExperimentalCoroutinesApi
  fun setUp() {
    hiltRule.inject()
    fhirResourceDataSource = mockk()
    sharedPreferencesHelper = mockk()

    configurationRegistry =
      ConfigurationRegistry(
        context,
        fhirEngine,
        fhirResourceDataSource,
        sharedPreferencesHelper,
        dispatcherProvider,
        Faker.appConfigService(),
      )
    coEvery { fhirResourceDataSource.loadData(any()) } returns
      Bundle().apply { entry = mutableListOf() }
    Assert.assertNotNull(configurationRegistry)
    Faker.loadTestConfigurationRegistryData(fhirEngine, configurationRegistry)
  }

  @Test
  fun testLoadConfiguration() {
    coVerify { fhirEngine.get(ResourceType.Binary, testAppId) }
    Assert.assertNotNull(configurationRegistry.getAppConfigs())
    Assert.assertNotNull(configurationRegistry.getAppFeatureConfigs())
    Assert.assertNotNull(configurationRegistry.getFormConfigs())
    Assert.assertTrue(configurationRegistry.getFormConfigs()!!.isNotEmpty())
    Assert.assertNotNull(configurationRegistry.getSyncConfigs())
  }
}
