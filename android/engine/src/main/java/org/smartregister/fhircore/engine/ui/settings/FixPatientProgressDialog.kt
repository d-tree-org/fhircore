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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun FixPatientProgressDialog(
  progress: SettingsChannelUiEvent.FixPatientProgress,
  onDismissRequest: () -> Unit,
) {
  if (progress.totalSize.minus(1) == progress.currentIndex) {
    onDismissRequest()
  }

  Dialog(onDismissRequest = onDismissRequest) {
    Card(modifier = Modifier.padding(24.dp)) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(contentAlignment = Alignment.Center) {
          CircularProgressIndicator(
            progress = 1f,
            modifier = Modifier.size(90.dp),
            strokeWidth = 16.dp,
            color = Color.LightGray,
          )

          CircularProgressIndicator(
            progress = progress.currentIndex.toFloat().div(progress.totalSize),
            modifier = Modifier.size(90.dp),
            strokeWidth = 16.dp,
            strokeCap = StrokeCap.Round,
          )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "${progress.currentIndex} of ${progress.totalSize}",
          style = MaterialTheme.typography.h5,
          color = Color.Gray,
          fontWeight = FontWeight.Bold,
        )
        Text(text = "This takes a while, please wait ...")
      }
    }
  }
}

@Preview
@Composable
fun FixPatientProgressDialogPreview() {
  FixPatientProgressDialog(
    progress = SettingsChannelUiEvent.FixPatientProgress(2, 10),
  ) {}
}
