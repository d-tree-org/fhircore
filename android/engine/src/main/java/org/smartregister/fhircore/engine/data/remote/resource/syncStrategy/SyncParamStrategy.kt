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

package org.smartregister.fhircore.engine.data.remote.resource.syncStrategy

import org.apache.commons.lang3.tuple.MutablePair
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.ListResource
import org.hl7.fhir.r4.model.ResourceType
import org.smartregister.fhircore.engine.R
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper

class SyncParamStrategy(val sharedPreferencesHelper: SharedPreferencesHelper) {
  private val context = sharedPreferencesHelper.context
  private val system = context.getString(R.string.sync_strategy_organization_system)
  private val meta = context.getString(R.string.sync_strategy_patient_meta)
  private val identifierList = context.getString(R.string.sync_strategy_patient_identifier_list)
  private val organization = sharedPreferencesHelper.organisationCode()
  private val tagSystem = "$system|$organization"
  private val tagMeta = "$meta|exposed-infant"
  private val identifierCoding = "$identifierList|$organization"
  private val syncParams = mutableListOf<Pair<ResourceType, Map<String, String>>>()

  fun syncParams(): Map<ResourceType, Map<String, String>> {
    listOf(
        ResourceType.Questionnaire,
        ResourceType.StructureMap,
        ResourceType.Patient,
        ResourceType.List,
      )
      .forEach {
        when (it) {
          ResourceType.Patient ->
            syncParams.add(
              MutablePair(
                  it,
                  mutableMapOf(
                    "_tag1" to tagMeta,
                    "_tag2" to tagSystem,
                    "_count" to "500",
                  ),
                )
                .toPair(),
            )
          ResourceType.List ->
            syncParams.add(MutablePair(it, mutableMapOf("code" to identifierCoding)).toPair())
          else -> syncParams.add(MutablePair(it, mutableMapOf("_count" to "500")).toPair())
        }
      }
    return syncParams.toMap()
  }
}

/**
 * @param resource [ListResource]
 * @return
 *   code=http://smartregister.org/fhir/patient-identifier-list%7Cded72f19-49be-41bc-9979-965604d50199
 */
private fun addCoding(resource: ListResource): ListResource {
  val theSystem = "http://smartregister.org/fhir/patient-identifier-list"
  val theCode = "ded72f19-49be-41bc-9979-965604d50199"
  val theDisplay = "Salima DHO"
  return resource.apply { code.addCoding(Coding(theSystem, theCode, theDisplay)) }
}
