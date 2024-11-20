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

import ca.uhn.fhir.rest.gclient.DateClientParam
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.Search
import com.google.android.fhir.search.search
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.smartregister.fhircore.engine.R
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirApiService
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceService
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.repository.ApiRepository
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.Progress
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SearchBy
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SearchBy.HUMAN_NAME
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SearchBy.IDENTIFIER
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SyncState
import org.smartregister.fhircore.engine.util.AppDataStore
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import timber.log.Timber

class ApiRepositoryImpl(
  private val fhirResourceService: FhirResourceService,
  private val fhirApiService: FhirApiService,
  private val sharedPreferencesHelper: SharedPreferencesHelper,
  private val fhirEngine: FhirEngine,
  private val appDataStore: AppDataStore,
) : ApiRepository() {

  private val context = sharedPreferencesHelper.context
  private val system = context.getString(R.string.sync_strategy_organization_system)
  private val tag = "$system%7C${sharedPreferencesHelper.organisationCode()}"

  override suspend fun fetchAndSaveToDb(
    logicalId: String,
    onCompleteListener: (Boolean) -> Unit,
  ) {
    var shouldRunSync = false
    runCatching { fhirApiService.getPatient(logicalId).entry }
      .onSuccess { bundleEntry ->
        bundleEntry
          .map { it.resource }
          .filterNot { resource ->
            runCatching { fhirEngine.get(resource.resourceType, resource.idPart) }.getOrNull() !=
              null
          }
          .takeIf { it.isNotEmpty() }
          ?.let { resources ->
            println("@@@@@@@@@@@@@@@@@@@ runSync @@@@@@@@@@@@@@@@@@@@@@@")
            fhirEngine.withTransaction { fhirEngine.create(*resources.toTypedArray()) }
            shouldRunSync = true
          }
      }
      .onFailure { Timber.e(it) }
    onCompleteListener(shouldRunSync)
  }

  override suspend fun search(searchQuery: String, criteria: SearchBy): List<Patient> =
    fhirResourceService
      .getResource(
        when (criteria) {
          IDENTIFIER -> "Patient?identifier=$searchQuery&_tag=$tag"
          HUMAN_NAME -> "Patient?given=$searchQuery,family=$searchQuery&_tag=$tag"
        },
      )
      .entry
      .map { it.resource as Patient }

  suspend fun nativeApi(logicalId: String, onProgress: (Progress) -> Unit) {
    runCatching {
        with(fhirResourceService) {
          val total = getResource("Patient/$logicalId/\$everything?_summary=count").total
          getResource("Patient/$logicalId/\$everything?_count=$total").entry
        }
      }
      .onSuccess { bundleEntry ->
        bundleEntry
          .map { it.resource }
          .forEachIndexed { index, resource ->
            fhirEngine.create(resource)
            onProgress(Progress(index, bundleEntry.size, logicalId))
          }
      }
      .onFailure { Timber.e(it) }
  }

  operator fun invoke(
    onProgress: (Progress) -> Unit,
    onCompleteListener: (Int) -> Unit,
  ) {
    onSyncListener(
      progressStatus = onProgress,
      onCompleteListener = { runApiFetch, patientSize ->
        sharedPreferencesHelper.write(
          SharedPreferenceKey.SYNC_STATUS.name,
          if (runApiFetch) {
            SyncState.CompletedInitialSync.value
          } else SyncState.SubSequentSync.value,
        )
        onCompleteListener(patientSize)
      },
    )
  }

  private fun onSyncListener(
    progressStatus: (Progress) -> Unit,
    onCompleteListener: (Boolean, Int) -> Unit,
  ) =
    CoroutineScope(Dispatchers.IO).launch {
      var patientSize: Int
      var runSync = false
      fhirEngine
        .search<Patient>(Search(ResourceType.Patient))
        .map { it.resource.idPart }
        .also { patientSize = it.size }
        .forEachIndexed { index, logicalId ->
          runCatching { fhirApiService.getPatient(logicalId).entry }
            .onSuccess { bundleEntry ->
              bundleEntry
                .map { it.resource }
                .filterNot { resource ->
                  runCatching { fhirEngine.get(resource.resourceType, resource.idPart) }
                    .getOrNull() != null
                }
                .takeIf { it.isNotEmpty() }
                ?.let { resources ->
                  println("@@@@@@@@@@@@@@@@@@@ runSync @@@@@@@@@@@@@@@@@@@@@@@")
                  runSync = true
                  fhirEngine.withTransaction { fhirEngine.create(*resources.toTypedArray()) }
                }
            }
            .onFailure { Timber.e(it) }
          progressStatus(Progress(index, patientSize, logicalId))
        }
      saveLastUpdatedTimestamp()
      onCompleteListener(runSync, patientSize)
    }

  private suspend inline fun <reified R : Resource> getLastUpdated() =
    fhirEngine
      .search<R> { sort(DateClientParam("_lastUpdated"), Order.DESCENDING) }
      .map { it.resource }
      .lastOrNull()

  private enum class OfType {
    Patient,
    Encounter,
    Observation,
    Condition,
    CarePlan,
    List,
    Task,
    Practitioner,
    RelatedPerson,
    Appointment,
  }

  private suspend fun saveLastUpdatedTimestamp() {
    OfType.entries
      .map {
        when (it) {
          OfType.Patient -> ResourceType.Patient
          OfType.Observation -> ResourceType.Observation
          OfType.CarePlan -> ResourceType.CarePlan
          OfType.Task -> ResourceType.Task
          OfType.Condition -> ResourceType.Condition
          OfType.Appointment -> ResourceType.Appointment
          OfType.Encounter -> ResourceType.Encounter
          OfType.List -> ResourceType.List
          OfType.Practitioner -> ResourceType.Practitioner
          OfType.RelatedPerson -> ResourceType.RelatedPerson
        }
      }
      .onEach { resourceType ->
        val lastSyncTimestamp = Date().toOffsetDateTime().formatLastSyncTimestamp()
        appDataStore.saveLastUpdatedTimestamp(resourceType, lastSyncTimestamp)
      }
  }

  private fun Date.toOffsetDateTime(): OffsetDateTime {
    return OffsetDateTime.ofInstant(toInstant(), ZoneId.systemDefault())
  }

  private fun OffsetDateTime.formatLastSyncTimestamp(): String {
    val syncTimestampFormatter =
      SimpleDateFormat(SYNC_TIMESTAMP_INPUT_FORMAT, Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
      }
    val parse: Date? = syncTimestampFormatter.parse(toString())
    return if (parse == null) "" else simpleDateFormat.format(parse)
  }

  companion object {
    const val SYNC_TIMESTAMP_INPUT_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
  }

  private val simpleDateFormat = SimpleDateFormat(SYNC_TIMESTAMP_INPUT_FORMAT, Locale.getDefault())
}
