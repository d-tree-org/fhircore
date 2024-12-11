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

package org.smartregister.fhircore.engine.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.WorkerParameters
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.search
import com.google.android.fhir.sync.AcceptLocalConflictResolver
import com.google.android.fhir.sync.ConflictResolver
import com.google.android.fhir.sync.DownloadWorkManager
import com.google.android.fhir.sync.FhirSyncWorker
import com.google.android.fhir.sync.upload.UploadStrategy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.ListResource
import org.hl7.fhir.r4.model.ResourceType
import org.smartregister.fhircore.engine.R
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.configuration.preferences.SyncUploadStrategy
import org.smartregister.fhircore.engine.data.local.TingatheDatabase
import org.smartregister.fhircore.engine.data.local.syncStrategy.toEntity
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.SyncParamStrategy
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.fhir.IdentifierSyncParams
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.fhir.LogicalIdSyncParamsBased
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.fhir.ResourceParamsBasedDownload
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.fhir.TimestampContext
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.getIdentifiers
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.hasCompletedInitialSync
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.logicalIds
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.onSendBroadcast
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.saveLastUpdatedTimestamp
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.syncConfigOfflineFirst
import org.smartregister.fhircore.engine.ui.questionnaire.ContentCache
import org.smartregister.fhircore.engine.util.AppDataStore
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.SharedPreferenceKey.SYNC_UPLOAD_STRATEGY
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import timber.log.Timber

@HiltWorker
class AppSyncWorker
@AssistedInject
constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val syncListenerManager: SyncListenerManager,
  val engine: FhirEngine,
  val dataStore: AppDataStore,
  val preference: SharedPreferencesHelper,
  val dispatcherProvider: DispatcherProvider,
  val configurationRegistry: ConfigurationRegistry,
  val syncBroadcaster: SyncBroadcaster,
  database: TingatheDatabase,
) : FhirSyncWorker(appContext, workerParams) {

  private val syncStrategyCacheDao = database.syncStrategyCacheDao
  private val broadcaster = LocalBroadcastManager.getInstance(dataStore.context.applicationContext)
  private val listResourceTitle = "Patient Identifier List"
  private val context = preference.context
  private val system = context.getString(R.string.sync_strategy_organization_system)
  private val organization = preference.organisationCode()
  private val tagSystem = "$system%7C$organization"

  private fun downloadWorkManager(): DownloadWorkManager = runBlocking {
    syncConfigOfflineFirst(configurationRegistry, preference)
      .takeIf { it }
      ?.let {
        getIdentifiers(engine)?.let { item ->
          return@runBlocking IdentifierSyncParams(item.data, tagSystem) {
            runBlocking {
              onSendBroadcast(broadcaster, it)
              syncStrategyCacheDao.insert(it.logicalId.toEntity())
              if (it.patientPositionAt == item.data.size) {
                purgeListResource()
              }
            }
          }
        }
      }

    val logicalIds = logicalIds(syncStrategyCacheDao)

    return@runBlocking if (logicalIds.isNotEmpty()) {
      LogicalIdSyncParamsBased(logicalIds) {
        Timber.e("${it.patientPositionAt} of ${logicalIds.size}")
        runBlocking {
          it.logicalId
            .toEntity()
            .map { catchEntity -> catchEntity.copy(shouldSync = true) }
            .also { syncStrategyCacheDao.upsert(it.map { it.logicalId }) }
          saveLastUpdatedTimestamp(dataStore)
          onSendBroadcast(broadcaster, it)
        }
      }
    } else {
      defaultDownloadManager()
    }
  }

  private suspend fun purgeListResource() {
    engine
      .search<ListResource> { filter(ListResource.TITLE, { value = listResourceTitle }) }
      .map { it.resource }
      .firstOrNull()
      ?.let {
        runCatching { engine.purge(ResourceType.List, it.idPart) }.onFailure { Timber.e(it) }
      }
  }

  private fun syncParams(): Map<ResourceType, Map<String, String>> {
    if (
      syncConfigOfflineFirst(
          configurationRegistry,
          preference,
        )
        .not()
    ) {
      return syncListenerManager.loadSyncParams()
    }
    return when {
      hasCompletedInitialSync(preference) -> SyncParamStrategy(preference).syncParams()
      else -> syncListenerManager.loadSyncParams()
    }
  }

  private fun defaultDownloadManager() =
    ResourceParamsBasedDownload(
      syncParams = syncParams(),
      context =
        object : TimestampContext {
          override suspend fun getLasUpdateTimestamp(resourceType: ResourceType): String =
            dataStore.getLastUpdateTimestamp(resourceType) ?: ""

          override suspend fun saveLastUpdatedTimestamp(
            resourceType: ResourceType,
            timestamp: String?,
          ) {
            timestamp?.let { dataStore.saveLastUpdatedTimestamp(resourceType, timestamp) }
          }
        },
    ) { ids ->
      if (syncConfigOfflineFirst(configurationRegistry, preference)) {
        runBlocking { syncStrategyCacheDao.upsert(ids) }
      }
    }

  override fun getConflictResolver(): ConflictResolver = AcceptLocalConflictResolver

  override fun getDownloadWorkManager(): DownloadWorkManager = downloadWorkManager()

  override suspend fun doWork(): Result {
    // Cache resources that might be needed urgent before sync
    ContentCache.saveResources(engine)

    return withContext(dispatcherProvider.singleThread()) { super.doWork() }
  }

  override fun getUploadStrategy(): UploadStrategy {
    val strategy =
      SyncUploadStrategy.valueOf(
        preference.read(
          SYNC_UPLOAD_STRATEGY.name,
          SyncUploadStrategy.Default.name,
        ) ?: SyncUploadStrategy.Default.name,
      )
    return when (strategy) {
      SyncUploadStrategy.Default -> {
        if (runAttemptCount % 2 == 0) {
          UploadStrategy.AllChangesSquashedBundlePut
        } else UploadStrategy.SingleResourcePut
      }
      SyncUploadStrategy.Single -> {
        UploadStrategy.SingleResourcePut
      }
      else -> {
        UploadStrategy.AllChangesSquashedBundlePut
      }
    }
  }

  override fun getFhirEngine(): FhirEngine = engine
}
