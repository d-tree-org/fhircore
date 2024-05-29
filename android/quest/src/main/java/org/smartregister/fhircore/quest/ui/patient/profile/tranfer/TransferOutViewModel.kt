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

package org.smartregister.fhircore.quest.ui.patient.profile.tranfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Patient
import org.smartregister.fhircore.engine.data.local.DefaultRepository
import org.smartregister.fhircore.engine.domain.util.DataLoadState
import org.smartregister.fhircore.quest.navigation.NavigationArg
import timber.log.Timber

@HiltViewModel
class TransferOutViewModel
@Inject
constructor(savedStateHandle: SavedStateHandle, private val defaultRepository: DefaultRepository) :
  ViewModel() {
  private val patientId: String = savedStateHandle[NavigationArg.PATIENT_ID]!!
  val state = MutableStateFlow<DataLoadState<TransferOutScreenState>>(DataLoadState.Loading)

  init {
    fetchPatientDetails()
  }

  fun fetchPatientDetails() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        state.value = DataLoadState.Loading
        val patient: Patient? = defaultRepository.loadResource<Patient>(patientId)
        if (patient == null) {
          state.value = DataLoadState.Error(java.lang.Exception("Failed to load patient"))
          return@launch
        }
        state.value = DataLoadState.Success(TransferOutScreenState.fromPatient(patient))
      } catch (e: Exception) {
        state.value = DataLoadState.Error(e)
      }
    }
  }

  fun transferPatient(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val value = state.value
        if (value is DataLoadState.Success) {
          val data = value.data
          val email = "ss@ddd.ccc"
          val subject = "Request for Patient Transfer out for ${data.fullName}"
          val body =
            """
          Where is the patient transferring to?
          
          Do you have any additional information you want to add?
          
          --------- Do not edit below this line ---------
          Current facility: ${data.facilityId}
          Patient id: ${data.patientId}
          Patient name: ${data.fullName}
                    """
              .trimIndent()
          composeEmail(context, arrayOf(email), subject, body)
        }
      } catch (e: Exception) {
        Timber.e(e)
      }
    }
  }
}

private fun composeEmail(
  context: Context,
  addresses: Array<String>,
  subject: String,
  body: String
) {
  val intent =
    Intent(Intent.ACTION_SENDTO).apply {
      setData(Uri.parse("mailto:"))
      putExtra(Intent.EXTRA_EMAIL, addresses)
      putExtra(Intent.EXTRA_SUBJECT, subject)
      putExtra(Intent.EXTRA_TEXT, body)
    }
  context.startActivity(intent)
}
