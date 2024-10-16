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

package org.smartregister.fhircore.quest.ui.main

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.sync.SyncJobStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.smartregister.fhircore.engine.appfeature.AppFeature
import org.smartregister.fhircore.engine.appfeature.AppFeatureManager
import org.smartregister.fhircore.engine.auth.AccountAuthenticator
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.configuration.app.ApplicationConfiguration
import org.smartregister.fhircore.engine.configuration.app.ConfigService
import org.smartregister.fhircore.engine.sync.SyncBroadcaster
import org.smartregister.fhircore.engine.ui.login.LoginActivity
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.SecureSharedPreference
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import org.smartregister.fhircore.engine.util.extension.fetchLanguages
import org.smartregister.fhircore.engine.util.extension.getActivity
import org.smartregister.fhircore.engine.util.extension.launchActivityWithNoBackStackHistory
import org.smartregister.fhircore.engine.util.extension.refresh
import org.smartregister.fhircore.engine.util.extension.setAppLocale
import org.smartregister.fhircore.quest.navigation.SideMenuOptionFactory
import org.smartregister.p2p.utils.startP2PScreen
import timber.log.Timber

@HiltViewModel
class AppMainViewModel
@Inject
constructor(
  val accountAuthenticator: AccountAuthenticator,
  val syncBroadcaster: SyncBroadcaster,
  val sideMenuOptionFactory: SideMenuOptionFactory,
  val secureSharedPreference: SecureSharedPreference,
  val sharedPreferencesHelper: SharedPreferencesHelper,
  val configurationRegistry: ConfigurationRegistry,
  val configService: ConfigService,
  val dispatcherProvider: DispatcherProvider,
  val appFeatureManager: AppFeatureManager,
) : ViewModel() {

  val appMainUiState: MutableState<AppMainUiState> = mutableStateOf(appMainUiStateOf())

  val refreshDataState: MutableState<Int> = mutableStateOf(0)
  val completedTaskId: MutableStateFlow<String?> = MutableStateFlow(null)

  private val simpleDateFormat = SimpleDateFormat(SYNC_TIMESTAMP_OUTPUT_FORMAT, Locale.getDefault())

  private val applicationConfiguration: ApplicationConfiguration =
    configurationRegistry.getAppConfigs()

  fun retrieveAppMainUiState() {
    appMainUiState.value =
      appMainUiStateOf(
        appTitle = applicationConfiguration.applicationName,
        currentLanguage = loadCurrentLanguage(),
        username = secureSharedPreference.retrieveSessionUsername() ?: "",
        sideMenuOptions = sideMenuOptionFactory.retrieveSideMenuOptions(),
        lastSyncTime = retrieveLastSyncTimestamp() ?: "",
        languages = configurationRegistry.fetchLanguages(),
        enableDeviceToDeviceSync = appFeatureManager.isFeatureActive(AppFeature.DeviceToDeviceSync),
        // Disable in-app reporting -- Measure reports not well supported
        // enableReports = appFeatureManager.isFeatureActive(AppFeature.InAppReporting),
        enableReports = true,
      )
  }

  fun onEvent(event: AppMainEvent) {
    when (event) {
      is AppMainEvent.Logout ->
        accountAuthenticator.logout {
          event.context.getActivity()?.launchActivityWithNoBackStackHistory<LoginActivity>()
        }
      is AppMainEvent.SwitchLanguage -> {
        sharedPreferencesHelper.write(SharedPreferenceKey.LANG.name, event.language.tag)
        event.context.run {
          setAppLocale(event.language.tag)
          (this as Activity).refresh()
        }
      }
      is AppMainEvent.SyncData -> {
        appMainUiState.value = appMainUiState.value.copy(syncClickEnabled = false)
        appMainUiState.value = appMainUiState.value.copy(syncClickEnabled = true)
        run(resumeSync)
      }
      is AppMainEvent.RefreshAuthToken -> {
        Timber.e("Refreshing token")
        try {
          accountAuthenticator.refreshSessionAuthToken()?.let { bundle ->
            bundle.getParcelable<Intent>(AccountManager.KEY_INTENT).let { intent ->
              if (intent == null && bundle.containsKey(AccountManager.KEY_AUTHTOKEN)) {
                syncBroadcaster.runSync()
                return@let
              }
              intent!!
              appMainUiState.value = appMainUiState.value.copy(syncClickEnabled = true)
              intent.flags += Intent.FLAG_ACTIVITY_SINGLE_TOP
              event.launchManualAuth(intent)
            }
          }
        } catch (_: Exception) {}
      }
      AppMainEvent.ResumeSync -> {
        run(resumeSync)
      }
      is AppMainEvent.DeviceToDeviceSync -> startP2PScreen(context = event.context)
      is AppMainEvent.UpdateSyncState -> {
        when (event.state) {
          // Update register count when sync completes
          is SyncJobStatus.Succeeded,
          is SyncJobStatus.Failed, -> {
            // Notify subscribers to refresh views after sync
            updateRefreshState()
            appMainUiState.value =
              appMainUiState.value.copy(
                lastSyncTime = event.lastSyncTime ?: "",
                sideMenuOptions = sideMenuOptionFactory.retrieveSideMenuOptions(),
              )
          }
          else ->
            appMainUiState.value =
              appMainUiState.value.copy(lastSyncTime = event.lastSyncTime ?: "")
        }
      }
    }
  }

  fun updateRefreshState() {
    refreshDataState.value += 1
  }

  private val resumeSync = {
    syncBroadcaster.runSync()
    appMainUiState.value =
      appMainUiState.value.copy(
        sideMenuOptions = sideMenuOptionFactory.retrieveSideMenuOptions(),
      )
  }

  private fun loadCurrentLanguage() =
    Locale.forLanguageTag(
        sharedPreferencesHelper.read(SharedPreferenceKey.LANG.name, Locale.UK.toLanguageTag())!!,
      )
      .displayName

  fun formatLastSyncTimestamp(timestamp: OffsetDateTime): String {
    val syncTimestampFormatter =
      SimpleDateFormat(SYNC_TIMESTAMP_INPUT_FORMAT, Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
      }
    val parse: Date? = syncTimestampFormatter.parse(timestamp.toString())
    return if (parse == null) "" else simpleDateFormat.format(parse)
  }

  fun retrieveLastSyncTimestamp(): String? =
    sharedPreferencesHelper.read(SharedPreferenceKey.LAST_SYNC_TIMESTAMP.name, null)

  fun updateLastSyncTimestamp(timestamp: OffsetDateTime) {
    sharedPreferencesHelper.write(
      SharedPreferenceKey.LAST_SYNC_TIMESTAMP.name,
      formatLastSyncTimestamp(timestamp),
    )
  }

  fun onTimeOut(context: Context) {
    accountAuthenticator.logoutLocal()
  }

  fun onTaskComplete(id: String?) {
    viewModelScope.launch { id?.let { completedTaskId.emit(it) } }
  }

  companion object {
    const val SYNC_TIMESTAMP_INPUT_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
    const val SYNC_TIMESTAMP_OUTPUT_FORMAT = "hh:mm aa, MMM d"
  }
}
