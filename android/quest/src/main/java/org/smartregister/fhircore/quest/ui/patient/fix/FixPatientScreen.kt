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

package org.smartregister.fhircore.quest.ui.patient.fix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
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
import org.smartregister.fhircore.engine.domain.util.DataLoadState
import org.smartregister.fhircore.quest.R
import org.smartregister.fhircore.quest.ui.main.AppMainViewModel

@Composable
fun FixPatientScreen(
  viewModel: FixPatientViewModel = hiltViewModel(),
  navController: NavHostController,
  appMainViewModel: AppMainViewModel,
) {
  val state by viewModel.screenState.collectAsState()
  val fixState by viewModel.fixState.collectAsState()

  Scaffold(
    topBar = {
      Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colors.primary),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(vertical = 8.dp),
        ) {
          IconButton(onClick = { navController.popBackStack() }) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = Color.White,
            )
          }
          Text(
            text = stringResource(id = R.string.fix_patient),
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.weight(1f),
          )
        }
      }
    },
  ) { innerPadding ->
    Box(modifier = Modifier.padding(innerPadding)) {
      if (state is FixPatientState.ActionStart) {
        PatientFixActionStartContainer(fixState, { navController.navigateUp() }) {
          viewModel.startFix()
        }
      } else {
        PatientFixAllActionContainer()
      }
    }
  }
}

@Composable fun PatientFixAllActionContainer() {}

@Composable
fun PatientFixActionStartContainer(
  state: DataLoadState<Boolean>,
  close: () -> Unit,
  retry: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = Modifier.fillMaxSize(),
  ) {
    when (state) {
      is DataLoadState.Error ->
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Text(text = "Something went wrong while fetching data..")
          Button(onClick = retry) { Text(text = "Retry") }
        }
      is DataLoadState.Success -> {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Text(text = "Completed!")
          Button(onClick = close) { Text(text = "Close") }
        }
      }
      else -> {
        Text(
          text = "Fixing Patient",
          style = MaterialTheme.typography.h4.copy(color = Color.Gray),
        )
        CircularProgressIndicator()
      }
    }
  }
}
