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

package org.smartregister.fhircore.engine.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Task
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.getPreferenceFlow
import me.zhanghai.compose.preference.listPreference
import me.zhanghai.compose.preference.switchPreference
import org.smartregister.fhircore.engine.R
import org.smartregister.fhircore.engine.configuration.preferences.SyncUploadStrategy
import org.smartregister.fhircore.engine.ui.settings.views.DevMenu
import org.smartregister.fhircore.engine.ui.settings.views.ReportBottomSheet
import org.smartregister.fhircore.engine.ui.settings.views.UserProfileRow
import org.smartregister.fhircore.engine.ui.theme.BlueTextColor
import org.smartregister.fhircore.engine.ui.theme.DividerColor
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferenceKey.LAST_PURGE_KEY
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper

const val SYNC_TIMESTAMP_OUTPUT_FORMAT = "hh:mm aa, MMM d"

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SettingsScreen(
  modifier: Modifier = Modifier,
  navController: NavController? = null,
  settingsViewModel: SettingsViewModel = hiltViewModel(),
  devViewModel: DevViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val devMenuSheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
  val reportsSheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
  val scope = rememberCoroutineScope()

  ModalBottomSheetLayout(
    sheetState = reportsSheetState,
    sheetContent = { ReportBottomSheet(devViewModel) },
  ) {
    ModalBottomSheetLayout(
      sheetState = devMenuSheetState,
      sheetContent = { DevMenu(viewModel = devViewModel) },
    ) {
      Scaffold(
        topBar = {
          TopAppBar(
            title = {},
            navigationIcon = {
              IconButton(onClick = { navController?.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "")
              }
            },
          )
        },
      ) { paddingValues ->
        ProvidePreferenceLocals(
          flow =
            context
              .getSharedPreferences(SharedPreferencesHelper.PREFS_NAME, Context.MODE_PRIVATE)
              .getPreferenceFlow(),
        ) {
          LazyColumn(
            modifier = modifier.padding(paddingValues).padding(vertical = 20.dp),
          ) {
            item {
              InfoCard(profileData = settingsViewModel.profileData)
              Divider(color = DividerColor)
              //          UserProfileRow(
              //            icon = Icons.Rounded.Download,
              //            text = stringResource(R.string.re_fetch_practitioner),
              //            clickListener = settingsViewModel::fetchPractitionerDetails,
              //            modifier = modifier,
              //          )
              //              UserProfileRow(
              //                icon = Icons.Rounded.Sync,
              //                text = stringResource(id = R.string.sync),
              //                clickListener = settingsViewModel::runSync,
              //                modifier = modifier,
              //              )
              UserProfileRow(
                icon = Icons.Rounded.Report,
                text = stringResource(R.string.reports),
                clickListener = { scope.launch { reportsSheetState.show() } },
                modifier = modifier,
              )
              UserProfileRow(
                icon = Icons.Rounded.Task,
                text = stringResource(R.string.background_taks),
                clickListener = { scope.launch { devMenuSheetState.show() } },
                modifier = modifier,
              )
            }
            item {
              Divider(color = DividerColor, modifier = Modifier.padding(vertical = 10.dp))
              PreferenceCategory(title = { Text(text = "Preferences") })
            }

            listPreference(
              key = SharedPreferenceKey.SYNC_UPLOAD_STRATEGY.name,
              defaultValue = SyncUploadStrategy.Default.name,
              icon = {
                Icon(
                  imageVector = Icons.Rounded.Upload,
                  contentDescription = null,
                  tint = BlueTextColor,
                )
              },
              summary = { Text(text = it) },
              title = {
                Text(
                  text = "Upload strategy",
                )
              },
              values = SyncUploadStrategy.entries.map { it.name },
            )

            switchPreference(
              key = SharedPreferenceKey.SYNC_ON_SAVE.name,
              defaultValue = true,
              title = { Text(text = "Sync on form answered") },
              summary = {
                Text(
                  text =
                    "When disabled, form saves will not start sync automatically, you have to manually sync the changes.",
                  style = MaterialTheme.typography.caption
                )
              },
            )

            switchPreference(
              key = SharedPreferenceKey.PATIENT_FIX_TYPE.name,
              defaultValue = false,
              title = { Text(text = "Fix patients offline") },
              summary = {
                Text(
                  text =
                    "When enable the app will attempt to fix the patient completely offline which my fail",
                  style = MaterialTheme.typography.caption
                )
              },
            )

            item {
              Divider(color = DividerColor, modifier = Modifier.padding(vertical = 10.dp))
              PreferenceCategory(title = { Text(text = "Others") })
            }
            item {
              UserProfileRow(
                icon = Icons.AutoMirrored.Rounded.Logout,
                text = stringResource(id = R.string.logout),
                clickListener = { settingsViewModel.logoutUser(context) },
                modifier = modifier,
              )
            }
            item {
              val timestamp = settingsViewModel.sharedPreferences.read(LAST_PURGE_KEY.name, 0L)
              val simpleDateFormat =
                SimpleDateFormat(SYNC_TIMESTAMP_OUTPUT_FORMAT, Locale.getDefault())
              val text = context.resources.getString(R.string.last_purge)
              val dateFormat = simpleDateFormat.format(timestamp)

              if (timestamp > 0L) {
                UserProfileRow(
                  icon = Icons.Rounded.CleaningServices,
                  text = "$text: $dateFormat",
                  clickable = false,
                  modifier = modifier,
                )
              }
            }
          }
        }
      }
    }
  }
}
