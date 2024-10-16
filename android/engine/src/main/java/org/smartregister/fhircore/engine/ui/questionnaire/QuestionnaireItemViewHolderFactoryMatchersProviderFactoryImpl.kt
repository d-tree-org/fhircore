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

package org.smartregister.fhircore.engine.ui.questionnaire

import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.fhir.datacapture.QuestionnaireItemViewHolderFactoryMatchersProviderFactory
import com.google.android.fhir.datacapture.contrib.views.barcode.BarCodeReaderViewHolderFactory
import com.google.android.fhir.datacapture.extensions.asStringValue
import org.smartregister.fhircore.engine.ui.questionnaire.items.CustomQuestItemDataProvider
import org.smartregister.fhircore.engine.ui.questionnaire.items.LocationPickerViewHolderFactory
import org.smartregister.fhircore.engine.ui.questionnaire.items.patient.PatientPickerViewHolderFactory

class QuestionnaireItemViewHolderFactoryMatchersProviderFactoryImpl(
  private val customQuestItemDataProvider: CustomQuestItemDataProvider,
) : QuestionnaireItemViewHolderFactoryMatchersProviderFactory {

  override fun get(
    provider: String,
  ): QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatchersProvider {
    // Note: Returns irrespective of the 'provider' passed
    return QuestionnaireItemViewHolderFactoryMatchersProviderImpl(customQuestItemDataProvider)
  }

  class QuestionnaireItemViewHolderFactoryMatchersProviderImpl(
    private val customQuestItemDataProvider: CustomQuestItemDataProvider,
  ) : QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatchersProvider() {

    override fun get(): List<QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatcher> {
      return listOf(
        QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatcher(
          BarCodeReaderViewHolderFactory,
        ) { questionnaireItem ->
          questionnaireItem.getExtensionByUrl(BARCODE_URL).let {
            if (it == null) false else it.value.asStringValue() == BARCODE_NAME
          }
        },
        QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatcher(
          LocationPickerViewHolderFactory(
            customQuestItemDataProvider = customQuestItemDataProvider,
          ),
        ) { questionnaireItem ->
          questionnaireItem
            .getExtensionByUrl(LocationPickerViewHolderFactory.WIDGET_EXTENSION)
            .let {
              if (it == null) {
                false
              } else
                it.value.asStringValue() in
                  listOf(
                    LocationPickerViewHolderFactory.WIDGET_TYPE,
                    LocationPickerViewHolderFactory.WIDGET_TYPE_ALL,
                  )
            }
        },
        QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatcher(
          PatientPickerViewHolderFactory(
            customQuestItemDataProvider = customQuestItemDataProvider,
          ),
        ) { questionnaireItem ->
          questionnaireItem.getExtensionByUrl(PatientPickerViewHolderFactory.WIDGET_EXTENSION).let {
            if (it == null) {
              false
            } else
              it.value.asStringValue() in
                listOf(
                  PatientPickerViewHolderFactory.WIDGET_TYPE_GUARDIAN,
                  PatientPickerViewHolderFactory.WIDGET_TYPE_ALL,
                )
          }
        },
      )
    }

    companion object {
      private const val BARCODE_URL =
        "https://fhir.labs.smartregister.org/barcode-type-widget-extension"
      private const val BARCODE_NAME = "barcode"
    }
  }

  companion object {
    const val DEFAULT_PROVIDER = "default"
  }
}
