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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.smartregister.fhircore.engine.data.remote.fhir.resource.ResourceFixerService
import org.smartregister.fhircore.engine.domain.util.DataLoadState
import org.smartregister.fhircore.quest.navigation.NavigationArg
import timber.log.Timber

@HiltViewModel
class FixPatientViewModel
@Inject
constructor(
  savedStateHandle: SavedStateHandle,
  private val resourceFixerService: ResourceFixerService,
) : ViewModel() {
  val patientId = savedStateHandle.get<String>(NavigationArg.PATIENT_ID) ?: ""
  private val carePlanId = savedStateHandle.get<String>(NAVIGATION_ARG_CARE_PLAN)
  private val startState =
    savedStateHandle.get<String>(NAVIGATION_ARG_START)?.let { FixStartState.valueOf(it) }
      ?: FixStartState.All
  val screenState = MutableStateFlow<FixPatientState>(FixPatientState.AllActions)
  val fixState = MutableStateFlow<DataLoadState<Boolean>>(DataLoadState.Idle)

  init {
    viewModelScope.launch {
      if (startState == FixStartState.StartFix) {
        screenState.emit(FixPatientState.ActionStart)
        startFix()
      } else {
        screenState.emit(FixPatientState.AllActions)
      }
    }
  }

  fun startFix() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        fixState.emit(DataLoadState.Loading)
        if (carePlanId == null) {
          return@launch
        }
        resourceFixerService.fixCurrentCarePlan(patientId, carePlanId)
        fixState.emit(DataLoadState.Success(true))
      } catch (e: Exception) {
        Timber.e(e)
        fixState.value = DataLoadState.Error(e)
      }
    }
  }

  companion object {
    const val NAVIGATION_ARG_START = "fix_start_state"
    const val NAVIGATION_ARG_CARE_PLAN = "fix_care_plan"
  }
}

enum class FixStartState {
  All,
  StartFix,
}
