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

package org.smartregister.fhircore.engine.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.ResourceType
import org.smartregister.fhircore.engine.data.remote.fhir.helper.FhirHelperRepository
import org.smartregister.fhircore.engine.data.remote.model.helper.FacilityResultData
import org.smartregister.fhircore.engine.domain.util.DataLoadState
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper

@HiltViewModel
class FacilityReportViewModel
@Inject
constructor(val sharedPreferences: SharedPreferencesHelper, val repository: FhirHelperRepository) :
  ViewModel() {
  val statsFlow = MutableStateFlow<DataLoadState<FacilityResultData>>(DataLoadState.Loading)

  init {
    loadStats()
  }

  fun loadStats() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        statsFlow.value = DataLoadState.Loading
        val location =
          sharedPreferences
            .read<List<String>>(
              key = ResourceType.Location.name,
              decodeWithGson = true,
            )
            ?.firstOrNull() ?: throw Exception("Failed to get location Id")
        val data = repository.getFacilityStats(location)
        statsFlow.value = DataLoadState.Success(data)
      } catch (e: Exception) {
        statsFlow.value = DataLoadState.Error(e)
      }
    }
  }
}
