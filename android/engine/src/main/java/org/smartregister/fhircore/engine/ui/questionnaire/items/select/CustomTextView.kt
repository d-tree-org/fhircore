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

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.fhir.datacapture.extensions.tryUnwrapContext
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.smartregister.fhircore.engine.R

class CustomTextView<T>
@JvmOverloads
constructor(
  context: Context,
  attrs: AttributeSet? = null,
  private val transformItem: ((T) -> SelectedOption<T>)? = null,
) : FrameLayout(context, attrs) {

  private var textInputLayout: TextInputLayout
  private var textInputEditText: TextInputEditText
  private var titleTextView: TextView

  private var items: List<T> = listOf()
  private var selectedOption: T? = null
  var onItemClickListener: ((T) -> Unit)? = null

  init {
    LayoutInflater.from(context).inflate(R.layout.custom_material_spinner, this, true)

    textInputLayout = findViewById(R.id.textInputLayout)
    textInputEditText = findViewById(R.id.textInputEditText)
    titleTextView = findViewById(R.id.helper_text)

    textInputEditText.isFocusable = false
    textInputEditText.isClickable = true

    textInputEditText.setOnClickListener { showSearchableDialog() }
  }

  private fun showSearchableDialog() {
    val activity =
      requireNotNull(context.tryUnwrapContext()) {
        "Can only use dialog select in an AppCompatActivity context"
      }
    val fragment =
      SearchableSelectDialogFragment(
        title = titleTextView.text,
        selectedOptions = items.map { transformItem?.invoke(it) ?: defaultItemTransform(it) },
      ) {
        selectedOption = it?.item
        selectedOption?.let { it1 -> onSelected(it1) }
      }
    fragment.show(activity.supportFragmentManager, null)
  }

  private fun onSelected(item: T) {
    textInputEditText.setText((transformItem?.invoke(item) ?: defaultItemTransform(item) ).title)
    onItemClickListener?.invoke(item)
  }

  private fun defaultItemTransform(item: T): SelectedOption<T> {
    return SelectedOption(item.toString(), item.hashCode().toString(), item)
  }

  fun setItems(locations: List<T>) {
    items = locations
  }

  fun setTitle(name: String, selectedItem: T? = null) {
    titleTextView.text = name
    if (selectedItem != null) {
      textInputEditText.setText((transformItem?.invoke(selectedItem) ?: defaultItemTransform(selectedItem) ).title)
    }
  }

  fun toggleEnable(enabled: Boolean) {
    textInputLayout.isEnabled = enabled
    textInputEditText.isEnabled = enabled
  }
}
