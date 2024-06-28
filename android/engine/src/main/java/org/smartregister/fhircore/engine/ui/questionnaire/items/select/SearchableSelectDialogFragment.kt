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

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.res.use
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.fhir.datacapture.views.MarginItemDecoration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import org.smartregister.fhircore.engine.R

data class SelectedOption<T>(
  val title: String,
  val id: String,
  val item: T,
)

class SearchableSelectDialogFragment<T>(
  private val title: CharSequence,
  private val selectedOptions: List<SelectedOption<T>>,
  private val onSelectedSave: (SelectedOption<T>?) -> Unit,
) : DialogFragment() {

  private var selectedOption: SelectedOption<T>? = null
  private var adapter: ArrayAdapterRecyclerViewAdapter<T>? = null

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    isCancelable = false

    val themeId =
      requireContext()
        .obtainStyledAttributes(com.google.android.fhir.datacapture.R.styleable.QuestionnaireTheme)
        .use {
          it.getResourceId(
            com.google.android.fhir.datacapture.R.styleable.QuestionnaireTheme_questionnaire_theme,
            com.google.android.fhir.datacapture.R.style.Theme_Questionnaire,
          )
        }

    val dialogThemeContext = ContextThemeWrapper(requireContext(), themeId)

    val view =
      LayoutInflater.from(dialogThemeContext).inflate(R.layout.dialog_searchable_list, null)

    view.findViewById<TextView>(R.id.dialog_title).text = title

    val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
    recyclerView.layoutManager = LinearLayoutManager(requireContext())
    recyclerView.addItemDecoration(
      MarginItemDecoration(
        marginVertical =
          resources.getDimensionPixelOffset(
            com.google.android.fhir.datacapture.R.dimen.option_item_margin_vertical,
          ),
        marginHorizontal =
          resources.getDimensionPixelOffset(
            com.google.android.fhir.datacapture.R.dimen.option_item_margin_horizontal,
          ),
      ),
    )

    adapter = ArrayAdapterRecyclerViewAdapter(selectedOptions) { selectedOption = it }

    recyclerView.adapter = adapter

    view
      .findViewById<TextInputEditText>(R.id.searchEditText)
      .addTextChangedListener(
        object : TextWatcher {
          override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

          override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

          override fun afterTextChanged(s: Editable?) {
            val filteredList = selectedOptions.filter { it.title.contains(s.toString(), true) }
            adapter?.setItems(filteredList)
          }
        },
      )

    val dialog =
      MaterialAlertDialogBuilder(requireContext())
        .setView(view)
        .setNegativeButton(R.string.cancel) { _, _ -> }
        .setPositiveButton(R.string.select) { _, _ -> onSelectedSave(selectedOption) }
        .create()
        .apply {
          setOnShowListener {
            dialog?.window?.let {
              // Android: EditText in Dialog doesn't pull up soft keyboard
              // https://stackoverflow.com/a/9118027
              it.clearFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                  WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
              )
              // Adjust the dialog after the keyboard is on so that OK-CANCEL buttons are visible.
              // SOFT_INPUT_ADJUST_RESIZE is deprecated and the suggested alternative
              // setDecorFitsSystemWindows is available api level 30 and above.
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                it.setDecorFitsSystemWindows(false)
              } else {
                it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
              }
            }
          }
        }

    return dialog
  }
}

class ArrayAdapterRecyclerViewAdapter<T>(
  private var items: List<SelectedOption<T>>,
  val onSelect: (SelectedOption<T>?) -> Unit,
) : RecyclerView.Adapter<ArrayAdapterRecyclerViewAdapter<T>.ViewHolder>() {
  var selectedOption: Int? = null

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view =
      LayoutInflater.from(parent.context)
        .inflate(com.google.android.fhir.datacapture.R.layout.option_item_single, parent, false)
    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(items[position], position)
  }

  override fun getItemCount() = items.size

  fun setItems(filteredList: List<SelectedOption<T>>) {
    val diffCallback = ItemDiffCallback(items, filteredList)
    val diffResult = DiffUtil.calculateDiff(diffCallback)
    items = filteredList
    diffResult.dispatchUpdatesTo(this)
  }

  inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val radioButton: RadioButton =
      itemView.findViewById(com.google.android.fhir.datacapture.R.id.radio_button)

    fun bind(item: SelectedOption<T>, position: Int) {
      radioButton.text = item.title
      radioButton.isChecked = selectedOption == position
      radioButton.setOnCheckedChangeListener { _, checked ->
        if (checked) {
          val oldPosition = selectedOption
          selectedOption = position
          onSelect.invoke(item)
          oldPosition?.let { notifyItemChanged(it) }
          notifyItemChanged(position)
        }
      }
    }
  }
}

class ItemDiffCallback<T>(
  private val oldList: List<SelectedOption<T>>,
  private val newList: List<SelectedOption<T>>,
) : DiffUtil.Callback() {

  override fun getOldListSize(): Int {
    return oldList.size
  }

  override fun getNewListSize(): Int {
    return newList.size
  }

  override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
    return oldList[oldItemPosition].id == newList[newItemPosition].id
  }

  override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
    return oldList[oldItemPosition].id == newList[newItemPosition].id
  }
}
