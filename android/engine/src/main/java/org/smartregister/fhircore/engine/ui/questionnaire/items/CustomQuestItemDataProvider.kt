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

package org.smartregister.fhircore.engine.ui.questionnaire.items

import ca.uhn.fhir.rest.gclient.TokenClientParam
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.get
import com.google.android.fhir.search.Operation
import com.google.android.fhir.search.StringFilterModifier
import com.google.android.fhir.search.filter.TokenParamFilterCriterion
import com.google.android.fhir.search.search
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import org.hl7.fhir.r4.model.Binary
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Reference
import org.smartregister.fhircore.engine.domain.model.HealthStatus
import org.smartregister.fhircore.engine.domain.model.LocationHierarchy
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import org.smartregister.fhircore.engine.util.SystemConstants
import org.smartregister.fhircore.engine.util.extension.asReference
import org.smartregister.fhircore.engine.util.extension.extractAge
import org.smartregister.fhircore.engine.util.extension.extractName
import org.smartregister.fhircore.engine.util.extension.extractOfficialIdentifier
import timber.log.Timber

class CustomQuestItemDataProvider
@Inject
constructor(
  val sharedPreferencesHelper: SharedPreferencesHelper,
  val fhirEngine: FhirEngine,
  val gson: Gson,
) {

  fun fetchCurrentFacilityLocationHierarchies(): List<LocationHierarchy> {
    return try {
      val type = object : TypeToken<List<LocationHierarchy>>() {}.type
      sharedPreferencesHelper.readJsonArray<List<LocationHierarchy>>(
        SharedPreferenceKey.PRACTITIONER_LOCATION_HIERARCHIES.name,
        type,
      )
    } catch (e: Exception) {
      Timber.e(e)
      listOf()
    }
  }

  suspend fun fetchAllFacilityLocationHierarchies(): List<LocationHierarchy> {
    return try {
      val binary: Binary = fhirEngine.get(SystemConstants.LOCATION_HIERARCHY_BINARY)
      val config = gson.fromJson(binary.content.decodeToString(), LocationHierarchy::class.java)
      config.children
    } catch (e: Exception) {
      Timber.e(e)
      listOf()
    }
  }

  suspend fun searchPatients(query: String): List<PickerPatient> {
    val codings =
      listOf(HealthStatus.NEWLY_DIAGNOSED_CLIENT, HealthStatus.CLIENT_ALREADY_ON_ART)
        .map {
          Coding().apply {
            system = SystemConstants.PATIENT_TYPE_FILTER_TAG_VIA_META_CODINGS_SYSTEM
            code = it.name.lowercase().replace("_", "-")
          }
        }
        .map<
          Coding,
          TokenParamFilterCriterion.() -> Unit,
        > { c ->
          { value = of(c) }
        }
    val patients =
      fhirEngine.search<Patient> {
        filter(Patient.ACTIVE, { value = of(true) })
        filter(Patient.DECEASED, { value = of(false) })
        filter(
          Patient.GENDER,
          { value = of("male") },
          { value = of("female") },
          operation = Operation.OR,
        )
        filter(TokenClientParam("_tag"), *codings.toTypedArray(), operation = Operation.OR)
        if (query.contains(Regex("[0-9]"))) {
          filter(Patient.IDENTIFIER, { value = of(query) })
        } else {
          filter(
            Patient.NAME,
            {
              modifier = StringFilterModifier.CONTAINS
              value = query
            },
          )
        }
        count = 5
      }

    return patients.map {
      val ref = it.resource.asReference()
      val name = it.resource.extractName()
      ref.display = name
      PickerPatient(
        name = name,
        id = it.resource.extractOfficialIdentifier(),
        gender = it.resource.gender.name,
        age = it.resource.extractAge(),
        reference = ref,
      )
    }
  }
}

data class PickerPatient(
  val name: String,
  val id: String?,
  val gender: String,
  val age: String,
  val reference: Reference,
)
