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
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.fhir.datacapture.extensions.isPaginated
import com.google.android.fhir.get
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.smartregister.fhircore.engine.R
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
class TransferOutActivity : BaseMultiLanguageActivity(), View.OnClickListener {
  @Inject lateinit var dispatcherProvider: DefaultDispatcherProvider
  lateinit var fragment: QuestionnaireFragment

  @Inject lateinit var fhirEngine: FhirEngine
  private val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()

  private val viewModel: TransferOutViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_questionnaire)
    lifecycleScope.launch {
      withContext(dispatcherProvider.main()) {
        // Only add the fragment once, when the activity is first created.
        if (savedInstanceState == null || !this@TransferOutActivity::fragment.isInitialized) {
          renderFragment()
        }
      }
    }
  }

  private suspend fun renderFragment() {
    // todo: load tranfer questionnaire
    val questionnaire: Questionnaire = fhirEngine.get(id = "patient-transfer-out")
    val questionnaireString = parser.encodeResourceToString(questionnaire)
    val questionnaireResponse: QuestionnaireResponse = viewModel.generateResponse(questionnaire)
    val questionnaireType = QuestionnaireType.DEFAULT

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
      //        Timber.e(it.encodeResourceToString())
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
            v.findViewById<Button>(com.google.android.fhir.datacapture.R.id.submit_questionnaire)
              ?.apply {
                layoutParams.width =
                  ViewGroup.LayoutParams
                    .MATCH_PARENT // Override by Styles xml does not seem to work for this layout
                // param
                //                                text = submitButtonText()
              }
          }
        }
      },
      false,
    )
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
      //            onSubmitRequestResult()
    }

    supportFragmentManager.addFragmentOnAttachListener { _, frag ->
      Timber.e(frag.tag?.uppercase())
    }

    fun submitButtonText(): String {
      return if (questionnaireType.isReadOnly() || questionnaire.experimental) {
        getString(R.string.done)
      } else if (questionnaireType.isEditMode()) {
        getString(R.string.questionnaire_alert_submit_button_title)
      } else {
        getString(R.string.questionnaire_alert_submit_button_title)
      }
    }

    fun onSubmitRequestResult() {
      if (questionnaireType.isReadOnly()) {
        finish()
      } else {
        //                showFormSubmissionConfirmAlert()
      }
    }
  }

  override fun onClick(v: View?) {
    TODO("Not yet implemented")
  }

  companion object {
    fun launch(context: Context, patientId: String) {
      context.startActivity(
        Intent(context, TransferOutActivity::class.java)
          .putExtra(NavigationArg.PATIENT_ID, patientId)
      )
    }
  }
}
