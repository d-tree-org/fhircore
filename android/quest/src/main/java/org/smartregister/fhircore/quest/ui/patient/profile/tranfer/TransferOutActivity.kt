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

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.fhir.datacapture.extensions.isPaginated
import com.google.android.fhir.datacapture.validation.Invalid
import com.google.android.fhir.datacapture.validation.QuestionnaireResponseValidator
import com.google.android.fhir.get
import dagger.hilt.android.AndroidEntryPoint
import java.lang.Exception
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.smartregister.fhircore.engine.R
import org.smartregister.fhircore.engine.databinding.ActivityLoadableQuestionnaireBinding
import org.smartregister.fhircore.engine.domain.util.DataLoadState
import org.smartregister.fhircore.engine.ui.base.AlertDialogue
import org.smartregister.fhircore.engine.ui.base.BaseMultiLanguageActivity
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireActivity
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireItemViewHolderFactoryMatchersProviderFactoryImpl
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireType
import org.smartregister.fhircore.engine.util.DefaultDispatcherProvider
import org.smartregister.fhircore.engine.util.extension.distinctifyLinkId
import org.smartregister.fhircore.engine.util.extension.encodeResourceToString
import org.smartregister.fhircore.quest.navigation.NavigationArg
import timber.log.Timber

@AndroidEntryPoint
class TransferOutActivity : BaseMultiLanguageActivity() {
  @Inject lateinit var dispatcherProvider: DefaultDispatcherProvider
  private lateinit var fragment: QuestionnaireFragment

  @Inject lateinit var fhirEngine: FhirEngine
  private var saveProcessingAlertDialog: AlertDialog? = null
  private val viewModel: TransferOutViewModel by viewModels()

  private lateinit var binding: ActivityLoadableQuestionnaireBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityLoadableQuestionnaireBinding.inflate(layoutInflater)
    val view = binding.root
    setContentView(view)
    lifecycleScope.launch {
      withContext(dispatcherProvider.main()) {
        // Only add the fragment once, when the activity is first created.
        if (savedInstanceState == null || !this@TransferOutActivity::fragment.isInitialized) {
          renderFragment()
        }
      }
    }
  }

  private fun toggleViews(showLoader: Boolean, exception: Exception? = null) {
    if (exception != null) {
      binding.container.visibility = View.VISIBLE
      binding.loadingSection.visibility = View.GONE
      binding.loadingText.text = exception.message ?: "Error occurred"
      return
    }
    binding.errorImage.visibility = View.GONE
    binding.loadingSection.visibility = if (showLoader) View.VISIBLE else View.GONE
    binding.container.visibility = if (showLoader) View.GONE else View.VISIBLE
  }

  private suspend fun renderFragment() {
    viewModel.updateState.observe(this) { state ->
      when (state) {
        is DataLoadState.Loading -> {
          saveProcessingAlertDialog =
            AlertDialogue.showProgressAlert(
              this@TransferOutActivity,
              R.string.form_progress_message,
            )
        }
        is DataLoadState.Success -> {
          saveProcessingAlertDialog?.dismiss()
          finish()
        }
        is DataLoadState.Error -> {
          saveProcessingAlertDialog?.dismiss()
          AlertDialogue.showErrorAlert(
            this@TransferOutActivity,
            R.string.questionnaire_alert_invalid_message,
            R.string.questionnaire_alert_invalid_title,
          )
        }
        else -> {}
      }
    }
    viewModel.state.observe(this) { state ->
      when (state) {
        is DataLoadState.Loading -> {
          binding.loadingText.text = getText(R.string.opening_form)
          toggleViews(true)
        }
        is DataLoadState.Error -> {
          toggleViews(true, state.exception)
        }
        is DataLoadState.Success -> {
          val questionnaire = state.data.questionnaire
          val questionnaireResponse: QuestionnaireResponse = state.data.questionnaireResponse
          val questionnaireString = state.data.questionnaireString
          val questionnaireType = QuestionnaireType.DEFAULT

          setToolbar(questionnaire)

          val questionnaireFragmentBuilder =
            QuestionnaireFragment.builder()
              .setQuestionnaire(questionnaireString)
              .showReviewPageBeforeSubmit(questionnaire.isPaginated)
              .setShowSubmitButton(true)
              .setCustomQuestionnaireItemViewHolderFactoryMatchersProvider(
                QuestionnaireItemViewHolderFactoryMatchersProviderFactoryImpl.DEFAULT_PROVIDER,
              )
              .setIsReadOnly(questionnaireType.isReadOnly())

          questionnaireResponse.let {
            it.distinctifyLinkId()
            questionnaireFragmentBuilder.setQuestionnaireResponse(it.encodeResourceToString())
          }

          fragment = questionnaireFragmentBuilder.build()

          supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
              override fun onFragmentCreated(
                fm: FragmentManager,
                f: Fragment,
                savedInstanceState: Bundle?,
              ) {
                super.onFragmentCreated(fm, f, savedInstanceState)
              }

              override fun onFragmentViewCreated(
                fm: FragmentManager,
                f: Fragment,
                v: View,
                savedInstanceState: Bundle?,
              ) {
                super.onFragmentViewCreated(fm, f, v, savedInstanceState)
                if (f is QuestionnaireFragment) {
                  v.findViewById<Button>(
                      com.google.android.fhir.datacapture.R.id.submit_questionnaire,
                    )
                    ?.apply {
                      layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                      text = submitButtonText()
                    }
                }
              }
            },
            false,
          )
          toggleViews(false)
          supportFragmentManager.commit {
            replace(
              R.id.container,
              fragment,
              QuestionnaireActivity.QUESTIONNAIRE_FRAGMENT_TAG,
            )
          }
          supportFragmentManager.setFragmentResultListener(
            QuestionnaireFragment.SUBMIT_REQUEST_KEY,
            this,
          ) { _, _ ->
            AlertDialogue.showConfirmAlert(
              context = this,
              message = R.string.questionnaire_alert_submit_message,
              title = R.string.questionnaire_alert_submit_title,
              confirmButtonListener = { handleQuestionnaireSubmit(questionnaire) },
              confirmButtonText = R.string.questionnaire_alert_submit_button_title,
            )
          }

          supportFragmentManager.addFragmentOnAttachListener { _, frag ->
            Timber.e(frag.tag?.uppercase())
          }
        }
        DataLoadState.Idle -> {}
      }
    }
  }

  fun submitButtonText(): String = getString(R.string.questionnaire_alert_submit_button_title)

  private suspend fun getQuestionnaireResponse(): QuestionnaireResponse {
    val questionnaireFragment =
      supportFragmentManager.findFragmentByTag(QuestionnaireActivity.QUESTIONNAIRE_FRAGMENT_TAG)
        as QuestionnaireFragment
    return questionnaireFragment.getQuestionnaireResponse()
  }

  private suspend fun validQuestionnaireResponse(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ) =
    QuestionnaireResponseValidator.validateQuestionnaireResponse(
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
        context = this,
      )
      .values
      .flatten()
      .none { it is Invalid }

  private fun handleQuestionnaireSubmit(questionnaire: Questionnaire) {
    val doHandleQuestionnaireResponse = suspend {
      getQuestionnaireResponse()
        .takeIf { validQuestionnaireResponse(questionnaire, it) }
        ?.let {
          viewModel.extractAndSaveResources(
            context = this,
            questionnaireResponse = it,
          )
        }
    }
    lifecycleScope.launch { doHandleQuestionnaireResponse() }
  }

  fun setToolbar(questionnaire: Questionnaire) {
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      title = questionnaire.title
    }
  }

  override fun onSupportNavigateUp(): Boolean {
    finish()
    return true
  }

  companion object {
    fun launch(context: Context, patientId: String) {
      context.startActivity(
        Intent(context, TransferOutActivity::class.java)
          .putExtra(NavigationArg.PATIENT_ID, patientId),
      )
    }
  }
}
