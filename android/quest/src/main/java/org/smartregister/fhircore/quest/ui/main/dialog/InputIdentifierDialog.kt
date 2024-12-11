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

package org.smartregister.fhircore.quest.ui.main.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.Date
import java.util.UUID
import org.smartregister.fhircore.engine.ui.theme.GreyTextColor
import org.smartregister.fhircore.engine.util.extension.toAgeDisplay
import org.smartregister.fhircore.quest.ui.main.components.CLEAR
import org.smartregister.fhircore.quest.ui.main.components.SEARCH
import org.smartregister.fhircore.quest.ui.patient.register.PatientId

@Composable
fun InputIdentifierDialog(
  query: String,
  listOfIdentifiers: List<PatientId>,
  isSearching: Boolean,
  onEvent: (InputIdentifierUiEvent) -> Unit,
) {
  Dialog(onDismissRequest = { onEvent(InputIdentifierUiEvent.DismissRequest) }) {
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "Add Id Number",
          color = Color.Gray,
          modifier = Modifier.padding(8.dp).fillMaxWidth(),
          style = MaterialTheme.typography.h6,
          textAlign = TextAlign.Center,
        )
        Box {
          OutlinedTextField(
            colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.DarkGray),
            value = query,
            onValueChange = { onEvent(InputIdentifierUiEvent.ValueChange(it)) },
            maxLines = 1,
            singleLine = true,
            placeholder = {
              Text(
                color = GreyTextColor,
                text = "Enter ID",
              )
            },
            modifier = Modifier.padding(8.dp).fillMaxWidth().background(Color.White),
            leadingIcon = {
              Box(modifier = Modifier.size(24.dp)) {
                if (isSearching) {
                  CircularProgressIndicator()
                } else {
                  Icon(imageVector = Icons.Filled.Numbers, SEARCH)
                }
              }
            },
            trailingIcon = {
              Row {
                if (query.isNotEmpty()) {
                  IconButton(onClick = { onEvent(InputIdentifierUiEvent.ValueChange("")) }) {
                    Icon(imageVector = Icons.Filled.Clear, CLEAR, tint = Color.Gray)
                  }
                }
              }
            },
            keyboardOptions =
              KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.None,
              ),
          )
        }
        Card(
          elevation = 4.dp,
          modifier = Modifier.requiredHeightIn(max = 320.dp),
        ) {
          LazyColumn {
            itemsIndexed(
              items = listOfIdentifiers.sortedBy { it.identifier },
              key = { _, item -> item.uuid },
            ) { index, item ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
              ) {
                Text(
                  text = index.plus(1).toString(),
                  style = MaterialTheme.typography.h5,
                  modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                )
                Column {
                  Text(text = item.identifier)
                  Text(text = item.humanName)
                  Text(text = item.dateOfBirth.toAgeDisplay())
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                  onClick = { onEvent(InputIdentifierUiEvent.DeleteIdentifier(item)) },
                  modifier = Modifier.clip(RoundedCornerShape(40)).background(Color(0xFFB40E0E)),
                ) {
                  Icon(
                    imageVector = Icons.Default.Clear,
                    modifier = Modifier.size(20.dp),
                    contentDescription = null,
                    tint = Color.White,
                  )
                }
              }
              if (index < listOfIdentifiers.size.minus(1)) {
                Divider()
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
          enabled = listOfIdentifiers.isNotEmpty(),
          onClick = { onEvent(InputIdentifierUiEvent.SyncNow) },
        ) {
          Text(text = "Sync Now")
        }
      }
    }
  }
}

@Preview
@Composable
fun InputIdentifierDialogPreview() {
  InputIdentifierDialog(
    "123456",
    (1..4).toList().map {
      PatientId(
        uuid = UUID.randomUUID().toString(),
        identifier = "12345",
        dateOfBirth = Date(1213235574589),
        humanName = "John Doe",
      )
    },
    onEvent = {},
    isSearching = true,
  )
}
