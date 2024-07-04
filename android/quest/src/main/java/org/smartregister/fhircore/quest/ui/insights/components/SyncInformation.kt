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
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.fhir.sync.SyncJobStatus
import org.smartregister.fhircore.quest.ui.insights.InsightsViewModel

@Composable
fun SyncInformation(insightsViewModel: InsightsViewModel) {
  val status by insightsViewModel.syncStatus.collectAsState()

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
        Text(text = "Sync Status", style = MaterialTheme.typography.h4.copy(color = Color.Gray))
        Spacer(modifier = Modifier.height(8.dp))

        if (status != null) {
          when (status) {
            is SyncJobStatus.Started -> {
              Text(text = "Sync started at: ${status?.timestamp}")
            }
            is SyncJobStatus.InProgress -> {
              Text(text = "Sync in progress...")
              Text(text = "Operation: ${(status as SyncJobStatus.InProgress).syncOperation.name}")
              LinearProgressIndicator(
                progress =
                  if ((status as SyncJobStatus.InProgress).total > 0) {
                    (status as SyncJobStatus.InProgress).completed.toFloat() /
                      (status as SyncJobStatus.InProgress).total
                  } else {
                    0f
                  },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
              )
              Text(
                text =
                  "Completed: ${(status as SyncJobStatus.InProgress).completed} / ${(status as SyncJobStatus.InProgress).total}",
              )
            }
            is SyncJobStatus.Succeeded -> {
              Text(text = "Sync succeeded at: ${(status as SyncJobStatus.Succeeded).timestamp}")
            }
            is SyncJobStatus.Failed -> {
              Text(text = "Sync failed at: ${(status as SyncJobStatus.Failed).timestamp}")
              (status as SyncJobStatus.Failed).exceptions.forEach { exception ->
                Text(
                  text = "Error: ${exception.exception.message}",
                  color = MaterialTheme.colors.error,
                )
              }
            }
            else -> {}
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
          onClick = { insightsViewModel.runSync() },
          enabled =
            status == null || status is SyncJobStatus.Failed || status is SyncJobStatus.Succeeded,
        ) {
          Text(text = "Run sync")
        }
      }
    }
  }
}
