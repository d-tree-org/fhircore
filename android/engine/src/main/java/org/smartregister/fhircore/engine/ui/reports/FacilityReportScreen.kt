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

package org.smartregister.fhircore.engine.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Card
import androidx.compose.material.Chip
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.twotone.KeyboardArrowDown
import androidx.compose.material.icons.twotone.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import java.time.LocalDate
import java.time.LocalDateTime
import org.smartregister.fhircore.engine.R
import org.smartregister.fhircore.engine.data.remote.model.helper.FacilityResultData
import org.smartregister.fhircore.engine.data.remote.model.helper.GroupedSummaryItem
import org.smartregister.fhircore.engine.data.remote.model.helper.SummaryItem
import org.smartregister.fhircore.engine.domain.util.DataLoadState
import org.smartregister.fhircore.engine.ui.components.DataFetchingContainer
import org.smartregister.fhircore.engine.ui.theme.SubtitleTextColor
import org.smartregister.fhircore.engine.util.extension.format

@Composable
fun FacilityReportScreen(
  navController: NavController,
  viewModel: FacilityReportViewModel = hiltViewModel(),
) {
  val statsState by viewModel.statsFlow.collectAsState()

  FacilityReportScreenContainer(
    statsState = statsState,
    navigateUp = { navController.navigateUp() },
    refresh = viewModel::loadStats,
  )
}

@Composable
fun FacilityReportScreenContainer(
  statsState: DataLoadState<FacilityResultData>,
  navigateUp: () -> Unit,
  refresh: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.reports)) },
        navigationIcon = {
          IconButton(onClick = navigateUp) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        },
        contentColor = Color.White,
        backgroundColor = MaterialTheme.colors.primary,
        actions = {
          IconButton(onClick = refresh) { Icon(Icons.Filled.Refresh, contentDescription = "") }
        },
      )
    },
    backgroundColor = Color(0xfffbfbfb),
  ) { paddingValues ->
    DataFetchingContainer(
      state = statsState,
      retry = refresh,
      modifier = Modifier.padding(paddingValues).padding(horizontal = 16.dp).fillMaxSize(),
    ) {
      ReportContainer(it.data)
    }
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ReportContainer(facilityData: FacilityResultData) {
  LazyColumn {
    item { Box(Modifier.height(14.dp)) }
    item {
      Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        Chip(onClick = {}) { Text(text = facilityData.generatedDate.format()) }
      }
    }
    for (group in facilityData.groups.sortedBy { it.order }) {
      item {
        var collapsed by rememberSaveable { mutableStateOf(!group.startCollapsed) }
        Card(
          elevation = 1.dp,
          backgroundColor = Color.White,
          modifier = Modifier.padding(vertical = 6.dp),
        ) {
          Column(
            Modifier.padding(12.dp).fillMaxWidth(),
          ) {
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(4.dp).fillMaxWidth().clickable { collapsed = !collapsed },
            ) {
              Text(
                text = group.groupTitle,
                style = MaterialTheme.typography.h6,
                modifier = Modifier,
              )
              IconButton(onClick = { collapsed = !collapsed }) {
                Icon(
                  if (!collapsed) {
                    Icons.TwoTone.KeyboardArrowDown
                  } else Icons.TwoTone.KeyboardArrowUp,
                  contentDescription = "",
                )
              }
            }

            if (collapsed) {
              Box(modifier = Modifier.height(6.dp))
              repeat(group.summaries.count()) { idx ->
                val summary = group.summaries[idx]
                Column(
                  Modifier.fillMaxWidth().padding(12.dp),
                ) {
                  Text(
                    text = summary.name,
                    color = SubtitleTextColor,
                    style = MaterialTheme.typography.subtitle2,
                    modifier = Modifier.wrapContentWidth(),
                  )
                  Text(text = summary.value.toString(), style = MaterialTheme.typography.subtitle1)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Preview
@Composable
fun ReportContainerPreview() {
  val group =
    GroupedSummaryItem(
      "totals",
      "Totals",
      listOf(
        SummaryItem(name = "Newly diagnosed clients (all)", value = 2),
        SummaryItem(name = "Already on Art (all)", value = 2),
        SummaryItem(name = "Exposed infant (all)", value = 2),
      ),
      order = 1,
    )
  val data =
    FacilityResultData(
      groups =
        listOf(
          group.copy(
            startCollapsed = true,
          ),
          group,
        ),
      date = LocalDate.now(),
      generatedDate = LocalDateTime.now(),
    )

  FacilityReportScreenContainer(
    statsState = DataLoadState.Success(data),
    refresh = {},
    navigateUp = {},
  )
}
