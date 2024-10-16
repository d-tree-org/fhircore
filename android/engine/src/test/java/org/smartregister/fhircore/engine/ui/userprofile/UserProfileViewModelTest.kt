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

package org.smartregister.fhircore.engine.ui.userprofile

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.fhir.sync.SyncJobStatus
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.Shadows
import org.smartregister.fhircore.engine.app.AppConfigService
import org.smartregister.fhircore.engine.app.fakes.Faker
import org.smartregister.fhircore.engine.auth.AccountAuthenticator
import org.smartregister.fhircore.engine.configuration.app.ConfigService
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceDataSource
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceService
import org.smartregister.fhircore.engine.data.remote.shared.TokenAuthenticator
import org.smartregister.fhircore.engine.domain.model.Language
import org.smartregister.fhircore.engine.robolectric.RobolectricTest
import org.smartregister.fhircore.engine.sync.SyncBroadcaster
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.SecureSharedPreference
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class UserProfileViewModelTest : RobolectricTest() {

  @get:Rule var hiltRule = HiltAndroidRule(this)

  lateinit var userProfileViewModel: UserProfileViewModel
  lateinit var accountAuthenticator: AccountAuthenticator
  lateinit var secureSharedPreference: SecureSharedPreference
  lateinit var sharedPreferencesHelper: SharedPreferencesHelper

  @BindValue var configurationRegistry = Faker.buildTestConfigurationRegistry()

  @Inject lateinit var dispatcherProvider: DispatcherProvider
  var tokenAuthenticator: TokenAuthenticator = mockk()

  private lateinit var configService: ConfigService

  private val sharedSyncStatus: MutableSharedFlow<SyncJobStatus> = MutableSharedFlow()
  private lateinit var syncBroadcaster: SyncBroadcaster
  private val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
  private val resourceService: FhirResourceService = mockk()
  private lateinit var fhirResourceDataSource: FhirResourceDataSource

  @Before
  fun setUp() {
    hiltRule.inject()
    accountAuthenticator = mockk()
    secureSharedPreference = mockk()
    sharedPreferencesHelper = mockk()
    configService = AppConfigService(context = context)
    fhirResourceDataSource = spyk(FhirResourceDataSource(resourceService))

    every { tokenAuthenticator.sessionActive() } returns true

    syncBroadcaster =
      SyncBroadcaster(
        configurationRegistry,
        configService,
        fhirEngine = mockk(),
        sharedSyncStatus,
        dispatcherProvider = dispatcherProvider,
        appContext = context,
        tracer = mockk(),
        tokenAuthenticator = tokenAuthenticator,
        sharedPreferencesHelper = sharedPreferencesHelper,
      )
    userProfileViewModel =
      UserProfileViewModel(
        syncBroadcaster,
        accountAuthenticator,
        secureSharedPreference,
        sharedPreferencesHelper,
        configurationRegistry,
      )
  }

  @Test
  fun testRunSync() {
    userProfileViewModel.runSync()
  }

  @Test
  fun testRetrieveUsernameShouldReturnDemo() {
    every { secureSharedPreference.retrieveSessionUsername() } returns "demo"

    Assert.assertEquals("demo", userProfileViewModel.retrieveUsername())
    verify { secureSharedPreference.retrieveSessionUsername() }
  }

  @Test
  fun testLogoutUserShouldCallAuthLogoutService() {
    every { accountAuthenticator.logout(any<() -> Unit>()) } returns Unit

    userProfileViewModel.logoutUser(context)

    verify(exactly = 1) { accountAuthenticator.logout(any<() -> Unit>()) }
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    Assert.assertTrue(userProfileViewModel.onLogout.value!!)
  }

  @Test
  fun allowSwitchingLanguagesShouldReturnTrueWhenMultipleLanguagesAreConfigured() {
    val languages = listOf(Language("es", "Spanish"), Language("en", "English"))
    userProfileViewModel = spyk(userProfileViewModel)

    every { userProfileViewModel.languages } returns languages

    Assert.assertTrue(userProfileViewModel.allowSwitchingLanguages())
  }

  @Test
  fun allowSwitchingLanguagesShouldReturnFalseWhenConfigurationIsFalse() {
    val languages = listOf(Language("es", "Spanish"))
    userProfileViewModel = spyk(userProfileViewModel)

    every { userProfileViewModel.languages } returns languages

    Assert.assertFalse(userProfileViewModel.allowSwitchingLanguages())
  }

  @Test
  fun loadSelectedLanguage() {
    every { sharedPreferencesHelper.read(SharedPreferenceKey.LANG.name, "en-GB") } returns "fr"

    Assert.assertEquals("French", userProfileViewModel.loadSelectedLanguage())
    verify { sharedPreferencesHelper.read(SharedPreferenceKey.LANG.name, "en-GB") }
  }

  @Test
  fun setLanguageShouldCallSharedPreferencesHelperWriteWithSelectedLanguageTagAndPostValue() {
    val language = Language("es", "Spanish")
    var postedValue: Language? = null

    every { sharedPreferencesHelper.write(any(), any<String>()) } just runs

    userProfileViewModel.language.observeForever { postedValue = it }

    userProfileViewModel.setLanguage(language)

    Shadows.shadowOf(Looper.getMainLooper()).idle()

    verify { sharedPreferencesHelper.write(SharedPreferenceKey.LANG.name, "es") }
    Assert.assertEquals(language, postedValue!!)
  }

  @Test
  fun fetchLanguagesShouldReturnEnglishAndSwahiliAsModels() = runBlockingTest {
    val languages = userProfileViewModel.languages
    Assert.assertEquals("English", languages[0].displayName)
    Assert.assertEquals("en", languages[0].tag)
    Assert.assertEquals("Swahili", languages[1].displayName)
    Assert.assertEquals("sw", languages[1].tag)
  }

  @Test
  fun languagesLazyPropertyShouldRunFetchLanguagesAndReturnConfiguredLanguages() {
    val languages = userProfileViewModel.languages

    Assert.assertEquals("English", languages[0].displayName)
    Assert.assertEquals("en", languages[0].tag)
    Assert.assertEquals("Swahili", languages[1].displayName)
    Assert.assertEquals("sw", languages[1].tag)
  }
}
