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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.smartregister.fhircore.quest.R
import org.smartregister.fhircore.quest.ui.insights.InsightsViewModel

@Composable
fun RamInformation(insightsViewModel: InsightsViewModel) {
  val isRefreshingRamAvailabilityStats by
    insightsViewModel.isRefreshingRamAvailabilityStatsStateFlow.collectAsState()
  val ramAvailabilityStats by insightsViewModel.ramAvailabilityStatsStateFlow.collectAsState()

  Card(
    modifier = Modifier.padding(8.dp).fillMaxWidth().height(IntrinsicSize.Min),
  ) {
    Box(
      modifier = Modifier.padding(8.dp),
    ) {
      Column {
        Text(
          text = stringResource(R.string.ram_available),
          style = MaterialTheme.typography.h4.copy(color = Color.Gray),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = ramAvailabilityStats,
          style =
            if (isRefreshingRamAvailabilityStats) {
              MaterialTheme.typography.h2.copy(color = Color.Gray.copy(alpha = 0.5F))
            } else MaterialTheme.typography.h2,
        )
      }
    }
  }
}
