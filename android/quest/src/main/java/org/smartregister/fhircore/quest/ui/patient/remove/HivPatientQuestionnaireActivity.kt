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

package org.smartregister.fhircore.quest.ui.patient.remove

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.material.ExperimentalMaterialApi
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.smartregister.fhircore.engine.ui.base.AlertDialogue
import org.smartregister.fhircore.engine.ui.base.AlertIntent
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireActivity
import org.smartregister.fhircore.engine.util.extension.extractName
import org.smartregister.fhircore.quest.R
import org.smartregister.fhircore.quest.ui.main.AppMainActivity

class HivPatientQuestionnaireActivity : QuestionnaireActivity() {

  private val viewModel by viewModels<HivPatientViewModel>()

  private lateinit var profileId: String
  private lateinit var profileName: String

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    profileId = intent.extras?.getString(QUESTIONNAIRE_ARG_PATIENT_KEY) ?: ""

    viewModel.apply {
      isRemoved.observe(this@HivPatientQuestionnaireActivity) { if (it) onRemove() }
      isDiscarded.observe(this@HivPatientQuestionnaireActivity) { if (it) finish() }
      profile.observe(this@HivPatientQuestionnaireActivity) { onReceive(it) }
    }
    viewModel.fetch(profileId)
  }

  @OptIn(ExperimentalMaterialApi::class)
  fun onRemove() {
    val intent =
      Intent(this, AppMainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
      }
    run {
      startActivity(intent)
      finish()
    }
  }

  fun onReceive(profile: Patient) {
    profileName = profile.extractName()
  }

  override fun submitButtonText(): String = setRemoveButtonText()

  override fun onSubmitRequestResult() {
    handleQuestionnaireSubmit()
  }

  override fun handleQuestionnaireResponse(questionnaireResponse: QuestionnaireResponse) {
    dismissSaveProcessing()
    confirmationDialog(profileId = profileId, profileName = profileName)
  }

  private fun confirmationDialog(profileId: String, profileName: String) {
    AlertDialogue.showAlert(
      context = this,
      alertIntent = AlertIntent.CONFIRM,
      title = setRemoveDialogTitle(),
      message = setRemoveDialogMessage(profileName),
      confirmButtonListener = { dialog ->
        viewModel.remove(profileId)
        dialog.dismiss()
      },
      neutralButtonListener = { dialog ->
        viewModel.discard()
        dialog.dismiss()
      },
    )
  }

  fun setRemoveButtonText(): String = getString(R.string.remove_this_person)

  fun setRemoveDialogTitle(): String = getString(R.string.remove_this_person)

  fun setRemoveDialogMessage(profileName: String): String =
    getString(R.string.remove_hiv_patient_warning, profileName)
}
