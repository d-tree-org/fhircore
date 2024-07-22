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

package org.smartregister.fhircore.quest.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import org.smartregister.fhircore.quest.R
import org.smartregister.fhircore.quest.ui.insights.components.RamInformation
import org.smartregister.fhircore.quest.ui.insights.components.SyncInformation
import org.smartregister.fhircore.quest.ui.insights.components.UnsyncedResourcesInformation
import org.smartregister.fhircore.quest.ui.localChange.LocalChangeScreen
import org.smartregister.fhircore.quest.ui.localChange.LocalChangeViewModel

@Composable
fun InsightsScreen(
  navController: NavHostController,
  modifier: Modifier = Modifier,
  insightsViewModel: InsightsViewModel = hiltViewModel(),
  localChangeViewModel: LocalChangeViewModel = hiltViewModel(),
) {
  Scaffold(
    topBar = {
      Column(
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colors.primary),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = modifier.padding(vertical = 8.dp),
        ) {
          IconButton(onClick = { navController.popBackStack() }) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = Color.White,
            )
          }
          Text(
            text = stringResource(id = R.string.insights),
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.weight(1f),
          )
        }
      }
    },
  ) { innerPadding ->
    Box(modifier = modifier.padding(innerPadding)) {
      val isRefreshing by insightsViewModel.isRefreshing.collectAsState()
      val state = localChangeViewModel.state.collectAsState().value

      SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing),
        onRefresh = { insightsViewModel.refresh() },
        //        indicator = { _, _ -> }
      ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          item { RamInformation(insightsViewModel = insightsViewModel) }
          item { SyncInformation(insightsViewModel = insightsViewModel) }
          item { LocalChangeScreen(state = state, localChangeViewModel::onEvent) }
          item { UnsyncedResourcesInformation(insightsViewModel = insightsViewModel) }
        }
      }
    }
  }
}
