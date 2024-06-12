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

package org.smartregister.fhircore.engine.ui.questionnaire.items.patient

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.smartregister.fhircore.engine.domain.util.DataLoadState
import org.smartregister.fhircore.engine.ui.questionnaire.items.CustomQuestItemDataProvider
import org.smartregister.fhircore.engine.ui.questionnaire.items.PickerPatient
import timber.log.Timber

class PatientPickerViewModel(
  private val lifecycleCoroutineScope: CoroutineScope,
  private val customQuestItemDataProvider: CustomQuestItemDataProvider,
) {
  private val _state = MutableLiveData<DataLoadState<List<PickerPatient>>>()
  val state: LiveData<DataLoadState<List<PickerPatient>>>
    get() = _state

  fun submitText(input: String) {
    _state.value = DataLoadState.Loading
    lifecycleCoroutineScope.launch {
      try {
        val patients = customQuestItemDataProvider.searchPatients(input)
        _state.value = DataLoadState.Success(patients)
      } catch (e: Exception) {
        Timber.e(e)
        _state.value = DataLoadState.Error(e)
      }
    }
  }
}
