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

package org.smartregister.fhircore.quest.ui.insights.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.smartregister.fhircore.engine.domain.util.DataLoadState
import org.smartregister.fhircore.engine.ui.theme.LoginDarkColor
import org.smartregister.fhircore.quest.R
import org.smartregister.fhircore.quest.ui.insights.InsightsViewModel

@Composable
fun UnsyncedResourcesInformation(insightsViewModel: InsightsViewModel) {
  val state by insightsViewModel.unsyncedResourcesChangesState.collectAsState()
  Card(
    modifier = Modifier.padding(8.dp).fillMaxWidth().height(IntrinsicSize.Min),
  ) {
    Box(
      modifier = Modifier.padding(8.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.Start,
      ) {
        Text(
          text = "Unsynced Changes",
          style = MaterialTheme.typography.h4.copy(color = Color.Gray),
        )
        when (state) {
          is DataLoadState.Success -> {
            val unsyncedResources = (state as DataLoadState.Success<List<Pair<String, Int>>>).data

            if (unsyncedResources.isNotEmpty()) {
              repeat(unsyncedResources.size) {
                val unsynced = unsyncedResources[it]
                UnsyncedDataView(
                  first = unsynced.first,
                  second = unsynced.second.toString(),
                )
              }
            } else {
              Text(text = "No unsynced changes")
            }
          }
          is DataLoadState.Error -> {
            Box(Modifier.fillMaxWidth()) {
              Text(
                text = "Something went wrong while fetching insights",
                modifier =
                  Modifier.align(
                    Alignment.Center,
                  ),
              )
            }
          }
          else -> {
            Box(Modifier.fillMaxWidth()) {
              CircularProgressIndicator(
                modifier =
                  Modifier.align(
                    Alignment.Center,
                  ),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun UnsyncedDataView(
  first: String,
  second: String,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = first,
        fontSize = 16.sp,
        color = colorResource(id = R.color.gray),
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = second,
        color = LoginDarkColor,
        fontWeight = FontWeight.Bold,
      )
    }
  }

  Spacer(modifier = Modifier.height(8.dp))
}
