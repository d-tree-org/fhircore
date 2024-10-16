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

@file:OptIn(ExperimentalMaterialApi::class)

package org.smartregister.fhircore.quest.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Scaffold
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.smartregister.fhircore.engine.appfeature.model.HealthModule
import org.smartregister.fhircore.engine.domain.model.SideMenuOption
import org.smartregister.fhircore.engine.ui.settings.SettingsScreen
import org.smartregister.fhircore.quest.R
import org.smartregister.fhircore.quest.navigation.MainNavigationScreen
import org.smartregister.fhircore.quest.navigation.NavigationArg
import org.smartregister.fhircore.quest.ui.appointment.register.AppointmentRegisterScreen
import org.smartregister.fhircore.quest.ui.counters.CountersScreen
import org.smartregister.fhircore.quest.ui.family.profile.FamilyProfileScreen
import org.smartregister.fhircore.quest.ui.insights.InsightsScreen
import org.smartregister.fhircore.quest.ui.main.components.AppDrawer
import org.smartregister.fhircore.quest.ui.patient.fix.FixPatientScreen
import org.smartregister.fhircore.quest.ui.patient.fix.FixPatientViewModel
import org.smartregister.fhircore.quest.ui.patient.profile.PatientProfileScreen
import org.smartregister.fhircore.quest.ui.patient.profile.childcontact.ChildContactsProfileScreen
import org.smartregister.fhircore.quest.ui.patient.profile.guardians.GuardianRelatedPersonProfileScreen
import org.smartregister.fhircore.quest.ui.patient.profile.guardians.GuardiansRoute
import org.smartregister.fhircore.quest.ui.patient.register.PatientRegisterScreen
import org.smartregister.fhircore.quest.ui.report.measure.MeasureReportViewModel
import org.smartregister.fhircore.quest.ui.report.measure.measureReportNavigationGraph
import org.smartregister.fhircore.quest.ui.tracing.details.TracingHistoryDetailsScreen
import org.smartregister.fhircore.quest.ui.tracing.history.TracingHistoryScreen
import org.smartregister.fhircore.quest.ui.tracing.outcomes.TracingOutcomesScreen
import org.smartregister.fhircore.quest.ui.tracing.profile.TracingProfileScreen
import org.smartregister.fhircore.quest.ui.tracing.register.TracingRegisterScreen

@Composable
fun MainScreen(
  modifier: Modifier = Modifier,
  appMainViewModel: AppMainViewModel = hiltViewModel(),
) {
  val navController = rememberNavController()
  val scope = rememberCoroutineScope()
  val scaffoldState = rememberScaffoldState()
  val uiState: AppMainUiState = appMainViewModel.appMainUiState.value
  val openDrawer: (Boolean) -> Unit = { open: Boolean ->
    scope.launch {
      if (open) scaffoldState.drawerState.open() else scaffoldState.drawerState.close()
    }
  }

  BackHandler(enabled = scaffoldState.drawerState.isOpen) {
    scope.launch { scaffoldState.drawerState.close() }
  }

  Scaffold(
    drawerGesturesEnabled = scaffoldState.drawerState.isOpen,
    scaffoldState = scaffoldState,
    drawerContent = {
      AppDrawer(
        appTitle = uiState.appTitle,
        username = uiState.username,
        lastSyncTime = uiState.lastSyncTime,
        currentLanguage = uiState.currentLanguage,
        languages = uiState.languages,
        openDrawer = openDrawer,
        sideMenuOptions = uiState.sideMenuOptions,
        onSideMenuClick = appMainViewModel::onEvent,
        navController = navController,
        enableDeviceToDeviceSync = uiState.enableDeviceToDeviceSync,
        enableReports = uiState.enableReports,
        syncClickEnabled = uiState.syncClickEnabled,
      )
    },
    bottomBar = {
      // TODO Activate bottom nav via view configuration
      /* BottomScreenSection(
        navController = navController,
        mainNavigationScreens = MainNavigationScreen.appScreens
      )*/
    },
  ) { innerPadding ->
    Box(modifier = modifier.padding(innerPadding)) {
      AppMainNavigationGraph(
        navController = navController,
        mainNavigationScreens = MainNavigationScreen.appScreens,
        openDrawer = openDrawer,
        sideMenuOptions = uiState.sideMenuOptions,
        appMainViewModel = appMainViewModel,
      )
    }
  }
}

@Composable
private fun AppMainNavigationGraph(
  navController: NavHostController,
  mainNavigationScreens: List<MainNavigationScreen>,
  openDrawer: (Boolean) -> Unit,
  sideMenuOptions: List<SideMenuOption>,
  measureReportViewModel: MeasureReportViewModel = hiltViewModel(),
  appMainViewModel: AppMainViewModel,
) {
  val firstSideMenuOption = sideMenuOptions.first()
  val firstScreenTitle = stringResource(firstSideMenuOption.titleResource)
  NavHost(
    navController = navController,
    startDestination =
      MainNavigationScreen.Home.route +
        NavigationArg.routePathsOf(includeCommonArgs = true, NavigationArg.SCREEN_TITLE),
  ) {
    mainNavigationScreens.forEach {
      val commonNavArgs =
        NavigationArg.commonNavArgs(
          firstSideMenuOption.appFeatureName,
          firstSideMenuOption.healthModule,
        )

      when (it) {
        MainNavigationScreen.Home ->
          composable(
            route =
              "${it.route}${NavigationArg.routePathsOf(includeCommonArgs = true, NavigationArg.SCREEN_TITLE)}",
            arguments =
              commonNavArgs.plus(
                navArgument(NavigationArg.SCREEN_TITLE) {
                  type = NavType.StringType
                  nullable = true
                  defaultValue = firstScreenTitle
                },
              ),
          ) { stackEntry ->
            val screenTitle: String =
              stackEntry.arguments?.getString(NavigationArg.SCREEN_TITLE)
                ?: stringResource(R.string.all_clients)

            val healthModule: HealthModule =
              stackEntry.arguments?.getSerializable(NavigationArg.HEALTH_MODULE) as HealthModule?
                ?: HealthModule.HIV
            if (
              healthModule == HealthModule.HOME_TRACING ||
                healthModule == HealthModule.PHONE_TRACING
            ) {
              TracingRegisterScreen(navController = navController, screenTitle = screenTitle)
            } else if (healthModule == HealthModule.APPOINTMENT) {
              AppointmentRegisterScreen(screenTitle = screenTitle, navController = navController)
            } else {
              PatientRegisterScreen(
                navController = navController,
                openDrawer = openDrawer,
                screenTitle = screenTitle,
              )
            }
          }
        MainNavigationScreen.Counters ->
          composable(route = it.route) { CountersScreen(navController = navController) }
        MainNavigationScreen.Tasks -> composable(MainNavigationScreen.Tasks.route) {}
        MainNavigationScreen.Insights ->
          composable(route = it.route) { InsightsScreen(navController = navController) }
        MainNavigationScreen.Reports ->
          measureReportNavigationGraph(navController, measureReportViewModel)
        MainNavigationScreen.Settings ->
          composable(MainNavigationScreen.Settings.route) {
            SettingsScreen(navController = navController)
          }
        MainNavigationScreen.PatientProfile ->
          composable(
            route =
              "${it.route}${NavigationArg.routePathsOf(includeCommonArgs = true, NavigationArg.PATIENT_ID, NavigationArg.FAMILY_ID)}",
            arguments = commonNavArgs.plus(patientIdNavArgument()),
          ) {
            PatientProfileScreen(navController = navController, appMainViewModel = appMainViewModel)
          }
        MainNavigationScreen.FixPatientProfile ->
          composable(
            route =
              "${it.route}${NavigationArg.routePathsOf(includeCommonArgs = true, NavigationArg.PATIENT_ID, FixPatientViewModel.NAVIGATION_ARG_START, FixPatientViewModel.NAVIGATION_ARG_CARE_PLAN)}",
            arguments = commonNavArgs.plus(patientIdNavArgument()),
          ) {
            FixPatientScreen(navController = navController, appMainViewModel = appMainViewModel)
          }
        MainNavigationScreen.TracingProfile ->
          composable(
            route =
              "${it.route}${NavigationArg.routePathsOf(includeCommonArgs = true, NavigationArg.PATIENT_ID, NavigationArg.FAMILY_ID)}",
            arguments = commonNavArgs.plus(patientIdNavArgument()),
          ) {
            TracingProfileScreen(navController = navController, appViewModel = appMainViewModel)
          }
        MainNavigationScreen.PatientGuardians ->
          composable(
            route =
              "${it.route}/{${NavigationArg.PATIENT_ID}}${NavigationArg.routePathsOf(includeCommonArgs = true)}",
            arguments =
              commonNavArgs.plus(
                navArgument(NavigationArg.PATIENT_ID) { type = NavType.StringType },
              ),
          ) {
            GuardiansRoute(
              navigateRoute = { route -> navController.navigate(route) },
              onBackPress = { navController.navigateUp() },
            )
          }
        MainNavigationScreen.GuardianProfile ->
          composable(
            route =
              "${it.route}/{${NavigationArg.PATIENT_ID}}${NavigationArg.routePathsOf(includeCommonArgs = true, NavigationArg.ON_ART)}",
            arguments =
              commonNavArgs.plus(
                listOf(
                  navArgument(NavigationArg.PATIENT_ID) { type = NavType.StringType },
                  navArgument(NavigationArg.ON_ART) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "true"
                  },
                ),
              ),
          ) { stackEntry ->
            val onART = stackEntry.arguments?.getString(NavigationArg.ON_ART) ?: "true"
            if (onART.toBoolean()) {
              PatientProfileScreen(
                navController = navController,
                appMainViewModel = appMainViewModel,
              )
            } else {
              GuardianRelatedPersonProfileScreen(onBackPress = { navController.navigateUp() })
            }
          }
        MainNavigationScreen.FamilyProfile ->
          composable(
            route =
              "${it.route}${NavigationArg.routePathsOf(includeCommonArgs = true, NavigationArg.PATIENT_ID)}",
            arguments = commonNavArgs.plus(patientIdNavArgument()),
          ) {
            FamilyProfileScreen(navController = navController)
          }
        MainNavigationScreen.ViewChildContacts ->
          composable(
            route =
              "${it.route}${NavigationArg.routePathsOf(includeCommonArgs = true, NavigationArg.PATIENT_ID)}",
            arguments = commonNavArgs.plus(patientIdNavArgument()),
          ) {
            ChildContactsProfileScreen(navController = navController)
          }
        MainNavigationScreen.TracingHistory ->
          composable(
            route =
              "${it.route}${NavigationArg.routePathsOf(includeCommonArgs = true, NavigationArg.PATIENT_ID)}",
            arguments = commonNavArgs.plus(patientIdNavArgument()),
          ) {
            TracingHistoryScreen(navController = navController)
          }
        MainNavigationScreen.TracingOutcomes ->
          composable(
            route =
              "${it.route}${NavigationArg.routePathsOf(includeCommonArgs = true, NavigationArg.PATIENT_ID, NavigationArg.TRACING_ID)}",
            arguments =
              commonNavArgs.plus(
                listOf(
                  navArgument(NavigationArg.PATIENT_ID) { type = NavType.StringType },
                  navArgument(NavigationArg.TRACING_ID) {
                    type = NavType.StringType
                    nullable = false
                  },
                ),
              ),
          ) {
            TracingOutcomesScreen(navController = navController)
          }
        MainNavigationScreen.TracingHistoryDetails ->
          composable(
            route =
              "${it.route}${NavigationArg.routePathsOf(includeCommonArgs = true, NavigationArg.PATIENT_ID, NavigationArg.TRACING_ID, NavigationArg.TRACING_ENCOUNTER_ID, NavigationArg.SCREEN_TITLE)}",
            arguments =
              commonNavArgs.plus(
                listOf(
                  navArgument(NavigationArg.PATIENT_ID) { type = NavType.StringType },
                  navArgument(NavigationArg.TRACING_ID) { type = NavType.StringType },
                  navArgument(NavigationArg.TRACING_ENCOUNTER_ID) { type = NavType.StringType },
                  navArgument(NavigationArg.SCREEN_TITLE) { type = NavType.StringType },
                ),
              ),
          ) { stackEntry ->
            val screenTitle: String =
              stackEntry.arguments?.getString(NavigationArg.SCREEN_TITLE) ?: ""
            TracingHistoryDetailsScreen(screenTitle, navController = navController)
          }
      }
    }
  }
}

private fun patientIdNavArgument() =
  listOf(
    navArgument(NavigationArg.PATIENT_ID) {
      type = NavType.StringType
      nullable = true
      defaultValue = null
    },
    navArgument(NavigationArg.FAMILY_ID) {
      type = NavType.StringType
      nullable = true
      defaultValue = null
    },
  )
