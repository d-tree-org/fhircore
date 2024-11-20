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

import com.google.android.fhir.FhirEngine
import com.google.android.fhir.db.ResourceNotFoundException
import com.google.android.fhir.search.Search
import org.hl7.fhir.r4.model.ListResource
import org.hl7.fhir.r4.model.ResourceType
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.Progress
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SearchBy
import org.smartregister.fhircore.engine.util.SharedPreferenceKey.PATIENT_IDENTIFIER_LIST_TIMESTAMP
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import timber.log.Timber

class ListResourceExt(
  private val fhirEngine: FhirEngine,
  private val sharedPreferences: SharedPreferencesHelper,
  private val apiRepository: ApiRepositoryImpl,
) {

  private var invokeCounter = 0

  suspend fun onListResourceExt(
    onProgress: (Progress) -> Unit,
  ): Boolean {
    var shouldRunSync = false
    var size = 0
    shouldRun()
      ?.filterNotNull()
      ?.also { size = it.size }
      ?.forEachIndexed { index, identifier ->
        apiRepository.search(identifier.toString(), SearchBy.IDENTIFIER).onEach { patient ->
          kotlin
            .runCatching { fhirEngine.get(patient.resourceType, patient.idPart) }
            .onFailure { throwable ->
              if (throwable is ResourceNotFoundException) {
                apiRepository.fetchAndSaveToDb(
                  logicalId = patient.idPart,
                  onCompleteListener = { shouldRunSync = it },
                )
              }
            }
            .onSuccess { Timber.e("Skipping -> ${it.resourceType} - ${it.idPart}") }
        }
        onProgress(Progress(index, size, identifier.toString()))
      }

    return shouldRunSync
  }

  private suspend fun get() =
    fhirEngine
      .search<ListResource>(
        Search(ResourceType.List).apply {
          filter(ListResource.TITLE, { value = "Patient Identifier List" })
        },
      )
      .map { it.resource }
      .firstOrNull()

  suspend fun shouldRun(): List<Int?>? {
    invokeCounter += 1
    if (invokeCounter == 1) {
      val listResource: ListResource = get() ?: return null
      val preferenceKey = PATIENT_IDENTIFIER_LIST_TIMESTAMP.name
      val oldTimestamp = sharedPreferences.read(preferenceKey, 0L)
      val currentTimestamp = listResource.meta.lastUpdated.time
      if (oldTimestamp == 0L || oldTimestamp > currentTimestamp) {
        sharedPreferences.write(preferenceKey, currentTimestamp)
        return listResource.entry.map { entry -> entry.item.display.toIntOrNull() }.toList()
      }
      return null
    }
    return null
  }
}
