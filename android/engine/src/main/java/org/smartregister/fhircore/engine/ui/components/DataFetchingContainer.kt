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

package org.smartregister.fhircore.engine.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.smartregister.fhircore.engine.domain.util.DataLoadState

@Composable
fun <T> DataFetchingContainer(
  state: DataLoadState<T>,
  modifier: Modifier = Modifier,
  retry: () -> Unit,
  success: @Composable (state: DataLoadState.Success<T>) -> Unit,
) {
  Column(
    modifier,
  ) {
    when (state) {
      is DataLoadState.Error ->
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier.fillMaxSize(),
        ) {
          ErrorMessage(
            message = "Something went wrong while fetching data..",
            onClickRetry = retry,
          )
        }
      is DataLoadState.Success -> success(state)
      else ->
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier.fillMaxSize(),
        ) {
          CircularProgressBar(
            text = "Fetching data",
          )
        }
    }
  }
}
