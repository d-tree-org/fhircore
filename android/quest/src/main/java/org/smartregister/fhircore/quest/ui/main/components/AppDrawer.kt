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

package org.smartregister.fhircore.quest.ui.main.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.smartregister.fhircore.engine.domain.model.Language
import org.smartregister.fhircore.engine.domain.model.SideMenuOption
import org.smartregister.fhircore.engine.ui.theme.AppTitleColor
import org.smartregister.fhircore.engine.ui.theme.SideMenuBottomItemDarkColor
import org.smartregister.fhircore.engine.ui.theme.SideMenuDarkColor
import org.smartregister.fhircore.engine.ui.theme.SubtitleTextColor
import org.smartregister.fhircore.engine.util.annotation.ExcludeFromJacocoGeneratedReport
import org.smartregister.fhircore.quest.R
import org.smartregister.fhircore.quest.navigation.MainNavigationScreen
import org.smartregister.fhircore.quest.navigation.NavigationArg
import org.smartregister.fhircore.quest.ui.main.AppMainEvent

const val SIDE_MENU_ICON = "sideMenuIcon"

@Composable
fun AppDrawer(
  modifier: Modifier = Modifier,
  appTitle: String,
  username: String,
  lastSyncTime: String,
  currentLanguage: String,
  languages: List<Language>,
  navController: NavHostController,
  openDrawer: (Boolean) -> Unit,
  sideMenuOptions: List<SideMenuOption>,
  onSideMenuClick: (AppMainEvent) -> Unit,
  enableDeviceToDeviceSync: Boolean,
  enableReports: Boolean,
  syncClickEnabled: Boolean,
) {
  val context = LocalContext.current
  var expandLanguageDropdown by remember { mutableStateOf(false) }
  val authActivityLauncherForResult =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult(),
      onResult = {
        if (it.resultCode == Activity.RESULT_OK) {
          // Re-issue sync event
          onSideMenuClick(AppMainEvent.ResumeSync)
        }
      },
    )
  val syncEvent = AppMainEvent.SyncData { intent -> authActivityLauncherForResult.launch(intent) }

  Column(
    verticalArrangement = Arrangement.SpaceBetween,
    modifier = modifier.fillMaxHeight().background(SideMenuDarkColor),
  ) {
    Column(
      modifier.background(SideMenuDarkColor).padding(16.dp),
    ) {
      Text(
        text = appTitle,
        fontSize = 22.sp,
        color = AppTitleColor,
        modifier = modifier.padding(vertical = 16.dp),
      )
      LazyColumn {
        items(sideMenuOptions, { "${it.appFeatureName}|${it.healthModule.name}" }) { sideMenuOption,
          ->
          val title = stringResource(sideMenuOption.titleResource)

          SideMenuItem(
            iconResource = sideMenuOption.iconResource,
            title = title,
            endText = if (sideMenuOption.showCount) "${sideMenuOption.count}" else "",
            showEndText = sideMenuOption.showCount,
            onSideMenuClick = {
              openDrawer(false)
              navController.navigate(
                route =
                  MainNavigationScreen.Home.route +
                    NavigationArg.bindArgumentsOf(
                      Pair(NavigationArg.FEATURE, sideMenuOption.appFeatureName),
                      Pair(NavigationArg.HEALTH_MODULE, sideMenuOption.healthModule),
                      Pair(NavigationArg.SCREEN_TITLE, title),
                    ),
              )
            },
          )
        }
      }

      SideMenuItem(
        iconResource = org.smartregister.fhircore.engine.R.drawable.ic_reports,
        title = stringResource(R.string.counters),
        showEndText = false,
        onSideMenuClick = {
          openDrawer(false)
          navController.navigate(
            route = MainNavigationScreen.Counters.route,
          )
        },
      )

      if (enableReports) {
        SideMenuItem(
          iconResource = org.smartregister.fhircore.engine.R.drawable.ic_reports,
          title = stringResource(org.smartregister.fhircore.engine.R.string.reports),
          showEndText = false,
          onSideMenuClick = {
            openDrawer(false)
            navController.navigate(MainNavigationScreen.Reports.route)
          },
        )
      }
      if (enableDeviceToDeviceSync) {
        SideMenuItem(
          iconResource = org.smartregister.fhircore.engine.R.drawable.ic_sync,
          title = stringResource(R.string.device_to_device_sync),
          showEndText = false,
          onSideMenuClick = {
            openDrawer(false)
            onSideMenuClick(AppMainEvent.DeviceToDeviceSync(context))
          },
        )
      }
      if (languages.size > 1) {
        Box {
          SideMenuItem(
            iconResource = org.smartregister.fhircore.engine.R.drawable.ic_outline_language_white,
            title = stringResource(org.smartregister.fhircore.engine.R.string.language),
            showEndText = true,
            endText = currentLanguage,
            onSideMenuClick = { expandLanguageDropdown = true },
          )
          DropdownMenu(
            expanded = expandLanguageDropdown,
            onDismissRequest = { expandLanguageDropdown = false },
            modifier = modifier.wrapContentWidth(Alignment.End),
          ) {
            for (language in languages) {
              DropdownMenuItem(
                onClick = {
                  onSideMenuClick(AppMainEvent.SwitchLanguage(language, context))
                  expandLanguageDropdown = false
                },
              ) {
                Text(
                  modifier = modifier.fillMaxWidth(),
                  text = language.displayName,
                  fontSize = 18.sp,
                )
              }
            }
          }
        }
      }

      SideMenuItem(
        iconResource = org.smartregister.fhircore.engine.R.drawable.ic_insights,
        title = stringResource(R.string.insights, username),
        showEndText = false,
        onSideMenuClick = {
          openDrawer(false)
          navController.navigate(MainNavigationScreen.Insights.route)
        },
      )
      SideMenuItem(
        iconResource = R.drawable.ic_settings,
        title = stringResource(org.smartregister.fhircore.engine.R.string.settings, username),
        showEndText = false,
        onSideMenuClick = {
          openDrawer(false)
          navController.navigate(MainNavigationScreen.Settings.route)
        },
      )
      SideMenuItem(
        iconResource = org.smartregister.fhircore.engine.R.drawable.ic_logout_white,
        title = stringResource(org.smartregister.fhircore.engine.R.string.logout_user, username),
        showEndText = false,
        onSideMenuClick = { onSideMenuClick(AppMainEvent.Logout(context)) },
      )
    }
    Box(
      modifier =
        modifier
          .background(SideMenuBottomItemDarkColor)
          .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
      SideMenuItem(
        iconResource = org.smartregister.fhircore.engine.R.drawable.ic_sync,
        title = stringResource(org.smartregister.fhircore.engine.R.string.sync),
        endText = lastSyncTime,
        showEndText = true,
        endTextColor = SubtitleTextColor,
        onSideMenuClick = { onSideMenuClick(syncEvent) },
        enabled = syncClickEnabled,
      )
    }
  }
}

@Composable
fun SideMenuItem(
  modifier: Modifier = Modifier,
  iconResource: Int,
  title: String,
  endText: String = "",
  endTextColor: Color = Color.White,
  showEndText: Boolean,
  onSideMenuClick: () -> Unit,
  enabled: Boolean = true,
) {
  Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier =
      if (enabled) {
        modifier.fillMaxWidth().clickable { onSideMenuClick() }
      } else modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val alpha = if (enabled) ContentAlpha.high else ContentAlpha.disabled

    Row(
      modifier = modifier.padding(vertical = 16.dp).alpha(alpha),
    ) {
      Icon(
        modifier = modifier.padding(end = 10.dp).size(24.dp).alpha(alpha),
        painter = painterResource(id = iconResource),
        contentDescription = SIDE_MENU_ICON,
        tint = Color.White,
      )
      SideMenuItemText(title = title, textColor = Color.White.copy(alpha))
    }

    if (showEndText) {
      SideMenuItemText(title = endText, textColor = endTextColor.copy(alpha))
    }
  }
}

@Composable
fun SideMenuItemText(title: String, textColor: Color) {
  Text(text = title, color = textColor, fontSize = 18.sp)
}

@Preview(showBackground = true)
@ExcludeFromJacocoGeneratedReport
@Composable
fun AppDrawerPreview() {
  AppDrawer(
    appTitle = "MOH VTS",
    username = "Demo",
    lastSyncTime = "05:30 PM, Mar 3",
    currentLanguage = "English",
    navController = rememberNavController(),
    openDrawer = {},
    sideMenuOptions =
      listOf(
        SideMenuOption(
          appFeatureName = "AllFamilies",
          iconResource = org.smartregister.fhircore.engine.R.drawable.ic_user,
          titleResource = org.smartregister.fhircore.engine.R.string.clients,
          count = 4,
          showCount = true,
        ),
        SideMenuOption(
          appFeatureName = "ChildClients",
          iconResource = org.smartregister.fhircore.engine.R.drawable.ic_user,
          titleResource = org.smartregister.fhircore.engine.R.string.clients,
          count = 16,
          showCount = true,
        ),
        SideMenuOption(
          appFeatureName = "Reports",
          iconResource = org.smartregister.fhircore.engine.R.drawable.ic_reports,
          titleResource = org.smartregister.fhircore.engine.R.string.clients,
          showCount = false,
        ),
      ),
    onSideMenuClick = {},
    languages = listOf(Language("en", "English"), Language("sw", "Swahili")),
    enableDeviceToDeviceSync = true,
    enableReports = true,
    syncClickEnabled = true,
  )
}

@Preview(showBackground = true)
@ExcludeFromJacocoGeneratedReport
@Composable
fun AppDrawerPreviewSyncDisabled() {
  AppDrawer(
    appTitle = "MOH VTS",
    username = "Demo",
    lastSyncTime = "05:30 PM, Mar 3",
    currentLanguage = "English",
    navController = rememberNavController(),
    openDrawer = {},
    sideMenuOptions =
      listOf(
        SideMenuOption(
          appFeatureName = "AllFamilies",
          iconResource = org.smartregister.fhircore.engine.R.drawable.ic_user,
          titleResource = org.smartregister.fhircore.engine.R.string.clients,
          count = 4,
          showCount = true,
        ),
        SideMenuOption(
          appFeatureName = "ChildClients",
          iconResource = org.smartregister.fhircore.engine.R.drawable.ic_user,
          titleResource = org.smartregister.fhircore.engine.R.string.clients,
          count = 16,
          showCount = true,
        ),
        SideMenuOption(
          appFeatureName = "Reports",
          iconResource = org.smartregister.fhircore.engine.R.drawable.ic_reports,
          titleResource = org.smartregister.fhircore.engine.R.string.clients,
          showCount = false,
        ),
      ),
    onSideMenuClick = {},
    languages = listOf(Language("en", "English"), Language("sw", "Swahili")),
    enableDeviceToDeviceSync = true,
    enableReports = true,
    syncClickEnabled = false,
  )
}
