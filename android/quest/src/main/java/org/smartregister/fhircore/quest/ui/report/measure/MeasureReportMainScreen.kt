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

package org.smartregister.fhircore.quest.ui.report.measure

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import org.smartregister.fhircore.engine.ui.reports.FacilityReportScreen
import org.smartregister.fhircore.quest.navigation.MainNavigationScreen
import org.smartregister.fhircore.quest.navigation.MeasureReportNavigationScreen
import org.smartregister.fhircore.quest.navigation.NavigationArg
import org.smartregister.fhircore.quest.ui.report.measure.screens.MeasureReportPatientsScreen
import org.smartregister.fhircore.quest.ui.report.measure.screens.MeasureReportResultScreen
import org.smartregister.fhircore.quest.ui.report.measure.screens.ReportTypeSelectorScreen

fun NavGraphBuilder.measureReportNavigationGraph(
  navController: NavController,
  measureReportViewModel: MeasureReportViewModel,
) {
  navigation(
    startDestination = MeasureReportNavigationScreen.MeasureReportList.route,
    route = MainNavigationScreen.Reports.route,
  ) {
    // Display list of supported measures for reporting
    //    composable(MeasureReportNavigationScreen.MeasureReportList.route) {
    //      MeasureReportListScreen(
    //        navController = navController,
    //        dataList = measureReportViewModel.reportMeasuresList(),
    //        onReportMeasureClicked = { measureReportRowData ->
    //          measureReportViewModel.onEvent(
    //            MeasureReportEvent.OnSelectMeasure(measureReportRowData, navController),
    //          )
    //        },
    //      )
    //    }
    composable(MeasureReportNavigationScreen.MeasureReportList.route) {
      FacilityReportScreen(
        navController = navController,
      )
    }
    // Choose report type; for either individual or population
    composable(
      route =
        MeasureReportNavigationScreen.ReportTypeSelector.route +
          NavigationArg.routePathsOf(includeCommonArgs = false, NavigationArg.SCREEN_TITLE),
      arguments =
        listOf(
          navArgument(NavigationArg.SCREEN_TITLE) {
            type = NavType.StringType
            defaultValue = ""
          },
        ),
    ) { stackEntry ->
      val screenTitle: String = stackEntry.arguments?.getString(NavigationArg.SCREEN_TITLE) ?: ""
      ReportTypeSelectorScreen(
        screenTitle = screenTitle,
        navController = navController,
        measureReportViewModel = measureReportViewModel,
      )
    }

    // Page for selecting patient to evaluate their measure
    composable(MeasureReportNavigationScreen.PatientsList.route) {
      MeasureReportPatientsScreen(
        navController = navController,
        measureReportViewModel = measureReportViewModel,
      )
    }

    // Page for displaying measure report results
    composable(MeasureReportNavigationScreen.MeasureReportResult.route) {
      MeasureReportResultScreen(
        navController = navController,
        measureReportViewModel = measureReportViewModel,
      )
    }
  }
}
