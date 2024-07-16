package org.smartregister.fhircore.quest.ui.patient.profile.tranfer

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenResumed
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.fhir.datacapture.extensions.isPaginated
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.ResourceType
import org.smartregister.fhircore.engine.ui.base.BaseMultiLanguageActivity
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireActivity
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireItemViewHolderFactoryMatchersProviderFactoryImpl
import org.smartregister.fhircore.engine.util.DefaultDispatcherProvider
import org.smartregister.fhircore.engine.util.extension.decodeResourceFromString
import org.smartregister.fhircore.engine.util.extension.distinctifyLinkId
import org.smartregister.fhircore.engine.util.extension.encodeResourceToString
import org.smartregister.fhircore.engine.util.extension.generateMissingItems
import javax.inject.Inject
import com.google.android.fhir.datacapture.extensions.isPaginated
import com.google.android.fhir.get
import org.hl7.fhir.r4.model.Questionnaire
import org.smartregister.fhircore.engine.R
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireConfig
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireType
import timber.log.Timber

class TransferOutActivity : BaseMultiLanguageActivity(), View.OnClickListener {
    @Inject
    lateinit var dispatcherProvider: DefaultDispatcherProvider
    lateinit var fragment: QuestionnaireFragment
    @Inject
    lateinit var fhirEngine: FhirEngine
    private val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_questionnaire)
        lifecycleScope.launch {
        withContext(dispatcherProvider.main()) {
            // Only add the fragment once, when the activity is first created.
            if (savedInstanceState == null || !this@TransferOutActivity::fragment.isInitialized) {
                renderFragment()
            }
        }}
    }

    private suspend fun renderFragment() {
        // todo: load tranfer questionnaire
        var questionnaire: Questionnaire = fhirEngine.get( id = "patient-transfer-out")
        lateinit var questionnaireConfig: QuestionnaireConfig
        var questionnaireString = parser.encodeResourceToString(questionnaire)
        lateinit var questionnaireResponse: QuestionnaireResponse
        var questionnaireType = QuestionnaireType.DEFAULT


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
                                text = submitButtonText()
                            }
                    }
                }
            },
            false,
        )
        supportFragmentManager.commit { replace(
            R.id.container, fragment,
            QuestionnaireActivity.QUESTIONNAIRE_FRAGMENT_TAG
        ) }
        supportFragmentManager.setFragmentResultListener(
            QuestionnaireFragment.SUBMIT_REQUEST_KEY,
            this,
        ) { _, _ ->
            onSubmitRequestResult()
        }

        supportFragmentManager.addFragmentOnAttachListener { _, frag ->
            Timber.e(frag.tag?.uppercase())
        }

        fun submitButtonText(): String {
            return if (questionnaireType.isReadOnly() || questionnaire.experimental) {
                getString(R.string.done)
            } else if (questionnaireType.isEditMode()) {
                // setting the save button text from Questionnaire Config
                questionnaireConfig.saveButtonText
                    ?: getString(R.string.questionnaire_alert_submit_button_title)
            } else {
                getString(R.string.questionnaire_alert_submit_button_title)
            }
        }
        fun onSubmitRequestResult() {
            if (questionnaireType.isReadOnly()) {
                finish()
            } else {
                showFormSubmissionConfirmAlert()
            }
        }

    }

companion object {
    fun launch(context: Co) {

    }
}

}