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

package org.smartregister.fhircore.engine.ui.questionnaire.items.select

import androidx.lifecycle.ViewModel
import com.google.android.fhir.datacapture.views.factories.SelectedOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class SearchableSelectDialogFragmentViewModel : ViewModel() {
  private val linkIdsToSelectedOptionsFlow =
    mutableMapOf<String, MutableSharedFlow<SelectedOptions>>()

  fun getSelectedOptionsFlow(linkId: String): Flow<SelectedOptions> = selectedOptionsFlow(linkId)

  suspend fun updateSelectedOptions(linkId: String, selectedOptions: SelectedOptions) {
    selectedOptionsFlow(linkId).emit(selectedOptions)
  }

  private fun selectedOptionsFlow(linkId: String) =
    linkIdsToSelectedOptionsFlow.getOrPut(linkId) { MutableSharedFlow(replay = 0) }
}
