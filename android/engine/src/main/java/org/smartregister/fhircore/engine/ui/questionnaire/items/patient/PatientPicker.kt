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

package org.smartregister.fhircore.engine.ui.questionnaire.items.patient

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.fhir.datacapture.views.HeaderView
import com.google.android.material.card.MaterialCardView
import org.hl7.fhir.r4.model.Reference
import org.smartregister.fhircore.engine.R
import org.smartregister.fhircore.engine.databinding.CustomQuestPatientPickerDialogBinding
import org.smartregister.fhircore.engine.domain.util.DataLoadState
import org.smartregister.fhircore.engine.ui.questionnaire.items.CustomQuestItemDataProvider
import org.smartregister.fhircore.engine.ui.questionnaire.items.PickerPatient

class PatientPicker(
  private val context: AppCompatActivity,
  itemView: View,
  lifecycleScope: LifecycleCoroutineScope,
  customQuestItemDataProvider: CustomQuestItemDataProvider,
) {
  private var patientText: TextView?
  var headerView: HeaderView? = null
  private var cardView: CardView? = null

  private val helperText: TextView
  private var errorView: LinearLayout
  private var errorText: TextView

  private val viewModel: PatientPickerViewModel

  private var selectedPatient: Reference? = null
  private var initialValue: Reference? = null

  private var onPatientChanged: ((Reference?) -> Unit)? = null

  init {
    cardView = itemView.findViewById(R.id.patient_picker_container)
    patientText = itemView.findViewById(R.id.patient_title_text)
    helperText = itemView.findViewById(R.id.location_helper_text)
    errorView = itemView.findViewById(R.id.item_error_view)
    errorText = itemView.findViewById(R.id.error_text)
    headerView = itemView.findViewById(R.id.header)

    cardView?.setOnClickListener { showDropdownDialog() }

    viewModel = PatientPickerViewModel(lifecycleScope, customQuestItemDataProvider)
  }

  private fun showDropdownDialog() {
    val dialogBinding =
      CustomQuestPatientPickerDialogBinding.inflate(LayoutInflater.from(context), null, false)
    val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()
    dialogBinding.submitButton.setOnClickListener {
      val inputText = dialogBinding.inputEditText.text.toString()
      viewModel.submitText(inputText)
    }

    viewModel.state.observe(context) { state ->
      val isLoading = state is DataLoadState.Loading

      dialogBinding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
      dialogBinding.recyclerView.visibility = if (!isLoading) View.VISIBLE else View.GONE
      dialogBinding.emptyStateTextView.visibility = View.GONE
      dialogBinding.errorStateTextView.visibility =
        if (state is DataLoadState.Error) View.VISIBLE else View.GONE

      if (state is DataLoadState.Success) {
        dialogBinding.recyclerView.layoutManager = LinearLayoutManager(context)
        dialogBinding.recyclerView.adapter =
          ItemsAdapter(state.data) {
            selectedPatient = it.reference
            callOnPatientChange(it)
            dialog.dismiss()
          }

        dialogBinding.recyclerView.visibility =
          if (state.data.isNotEmpty()) View.VISIBLE else View.GONE
        dialogBinding.emptyStateTextView.visibility =
          if (state.data.isEmpty()) View.VISIBLE else View.GONE
      }
    }
    dialog.show()
  }

  private fun callOnPatientChange(patientPicker: PickerPatient?) {
    onPatientChanged?.invoke(selectedPatient)
    patientText?.text = patientPicker?.name
  }

  fun setOnPatientChanged(listener: ((Reference?) -> Unit)?) {
    onPatientChanged = listener
  }

  fun setRequiredOrOptionalText(requiredOrOptionalText: String?) {
    helperText.text = requiredOrOptionalText
    helperText.visibility = View.VISIBLE
  }

  fun setEnabled(enabled: Boolean) {
    cardView?.isEnabled = enabled
  }

  fun initAnswer(initialAnswer: Reference?) {
    if (initialAnswer != null && initialValue == null) {
      selectedPatient = initialAnswer
      patientText?.text = initialAnswer.display
      initialValue = initialAnswer
    }
  }

  fun showError(validationErrorMessage: String?) {
    if (validationErrorMessage == null) {
      errorView.visibility = View.GONE
      return
    }

    errorView.visibility = View.VISIBLE
    errorText.text = validationErrorMessage
  }
}

class ItemsAdapter(
  private val items: List<PickerPatient>,
  val onPatientSelected: (PickerPatient) -> Unit
) : RecyclerView.Adapter<ItemsAdapter.ViewHolder>() {

  class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
    val idTextView: TextView = itemView.findViewById(R.id.idTextView)
    val genderTextView: TextView = itemView.findViewById(R.id.genderTextView)
    val ageTextView: TextView = itemView.findViewById(R.id.ageTextView)
    val patientContainer: MaterialCardView = itemView.findViewById(R.id.patient_container)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view =
      LayoutInflater.from(parent.context)
        .inflate(R.layout.custom_patient_picker_item, parent, false)
    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val person = items[position]
    holder.nameTextView.text = person.name
    holder.idTextView.text = person.id
    holder.genderTextView.text = person.gender
    holder.ageTextView.text = person.age
    holder.patientContainer.setOnClickListener { onPatientSelected(person) }
  }

  override fun getItemCount(): Int {
    return items.size
  }
}
