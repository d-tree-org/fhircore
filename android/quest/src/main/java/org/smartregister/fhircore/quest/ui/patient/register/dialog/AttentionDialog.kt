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

package org.smartregister.fhircore.quest.ui.patient.register.dialog

import androidx.compose.MutableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun AttentionDialog(
  show: MutableState<Boolean>,
  onClick: () -> Unit,
) {
  if (show.value) {
    Dialog(
      onDismissRequest = { show.value = false },
      properties =
        DialogProperties(
          dismissOnClickOutside = false,
          dismissOnBackPress = false,
        ),
    ) {
      Card(modifier = Modifier.padding(24.dp)) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Spacer(modifier = Modifier.size(8.dp))
          Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            modifier = Modifier.size(62.dp),
            tint = Color(0xFFFFA726),
          )
          Text(
            text =
              "Please stay on this screen as the initial sync is in progress, we will notify you know when it is completed.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
          )
          Button(onClick = onClick) { Text(text = "Continue") }
        }
      }
    }
  }
}
