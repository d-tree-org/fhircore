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

package org.smartregister.fhircore.quest.ui.patient.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.smartregister.fhircore.quest.ui.shared.models.ProfileViewData

@Composable
fun ProfileErrorCard(
  profileViewData: ProfileViewData.PatientProfileViewData,
  navigate: () -> Unit,
) {
  if (profileViewData.hasMissingTasks) {
    Card(
      backgroundColor = MaterialTheme.colors.error,
      modifier = Modifier.fillMaxWidth().padding(8.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
        Text(text = "Error", style = MaterialTheme.typography.h6)
        Text(text = "Seems like this patient has some errors")
        Button(onClick = navigate) { Text(text = "Fix patient") }
      }
    }
  }
}

@Preview
@Composable
private fun ProfileErrorCardPreview() {
  Box(Modifier.padding(16.dp)) {
    ProfileErrorCard(
      profileViewData =
        ProfileViewData.PatientProfileViewData(
          hasMissingTasks = true,
        ),
      navigate = {},
    )
  }
}
