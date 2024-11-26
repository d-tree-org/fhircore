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
import com.google.android.fhir.search.search
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.hl7.fhir.r4.model.ListResource
import org.hl7.fhir.r4.model.Patient
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.configuration.Item
import org.smartregister.fhircore.engine.data.local.syncStrategy.SyncStrategyCacheDao
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SyncState
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferenceKey.SYNC_STATUS
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper

fun logicalIds(fhirEngine: FhirEngine) = runBlocking {
  fhirEngine
    .search<Patient> { filter(Patient.ACTIVE, { value = of(true) }) }
    .map { it.resource.idPart }
}

fun List<String>.subListIds(syncStrategyCacheDao: SyncStrategyCacheDao): List<String> {
  return runBlocking {
    val cachedIds = syncStrategyCacheDao.query().map { it.logicalId }
    ((cachedIds union this@subListIds) - (cachedIds intersect this@subListIds.toSet())).toList()
  }
}

fun hasCompletedInitialSync(sharedPreferencesHelper: SharedPreferencesHelper) =
  getSyncState(sharedPreferencesHelper) < SyncState.CompletedInitialSync.value

fun setSubSequentSync(sharedPreferencesHelper: SharedPreferencesHelper) =
  sharedPreferencesHelper.write(SYNC_STATUS.name, SyncState.SubSequentSync.value)

fun isSubSequentSync(sharedPreferencesHelper: SharedPreferencesHelper) =
  getSyncState(sharedPreferencesHelper) == SyncState.SubSequentSync.value

fun isRunSyncNow(sharedPreferencesHelper: SharedPreferencesHelper) =
  getSyncState(sharedPreferencesHelper) == SyncState.RunSyncNow.value

fun getSyncState(sharedPreferencesHelper: SharedPreferencesHelper) =
  sharedPreferencesHelper.read(SYNC_STATUS.name, SyncState.InitialSync.value)

fun Long.before1min(): Boolean {
  val currentTimeMillis = System.currentTimeMillis()
  val diffMillis = currentTimeMillis - this
  return TimeUnit.MILLISECONDS.toMinutes(diffMillis) < 1
}

fun String.splitIdTimestamp() = split("|")

fun toIdTimestamp(sharedPreferencesHelper: SharedPreferencesHelper): IdTimestamp? =
  sharedPreferencesHelper
    .read(SharedPreferenceKey.SEARCH_PATIENT_ID_TIMESTAMP.name, null)
    ?.splitIdTimestamp()
    ?.map { IdTimestamp(it.first().toString(), it.last().toString().toLong()) }
    ?.firstOrNull()

suspend fun getIdentifier(fhirEngine: FhirEngine) =
  fhirEngine
    .search<ListResource> { filter(ListResource.TITLE, { value = "Patient Identifier List" }) }
    .map { it.resource }
    .firstOrNull()

data class IdTimestamp(
  val logicalId: String,
  val timestamp: Long,
)

fun perOrgSyncConfig(
  configurationRegistry: ConfigurationRegistry,
  sharedPreferencesHelper: SharedPreferencesHelper,
): Item? =
  configurationRegistry.getPerOrgSyncConfigs()?.items?.find {
    it.id == sharedPreferencesHelper.organisationCode()
  }
