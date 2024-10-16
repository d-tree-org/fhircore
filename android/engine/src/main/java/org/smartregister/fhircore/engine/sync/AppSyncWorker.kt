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
import androidx.work.WorkerParameters
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.sync.AcceptLocalConflictResolver
import com.google.android.fhir.sync.ConflictResolver
import com.google.android.fhir.sync.DownloadWorkManager
import com.google.android.fhir.sync.FhirSyncWorker
import com.google.android.fhir.sync.download.ResourceParamsBasedDownloadWorkManager
import com.google.android.fhir.sync.upload.UploadStrategy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.ResourceType
import org.smartregister.fhircore.engine.configuration.preferences.SyncUploadStrategy
import org.smartregister.fhircore.engine.ui.questionnaire.ContentCache
import org.smartregister.fhircore.engine.util.AppDataStore
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper

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
) : FhirSyncWorker(appContext, workerParams) {
  override fun getConflictResolver(): ConflictResolver = AcceptLocalConflictResolver

  override fun getDownloadWorkManager(): DownloadWorkManager =
    ResourceParamsBasedDownloadWorkManager(
      syncParams = syncListenerManager.loadSyncParams(),
      context =
        object : ResourceParamsBasedDownloadWorkManager.TimestampContext {
          override suspend fun getLasUpdateTimestamp(resourceType: ResourceType): String =
            dataStore.getLastUpdateTimestamp(resourceType) ?: ""

          override suspend fun saveLastUpdatedTimestamp(
            resourceType: ResourceType,
            timestamp: String?,
          ) {
            timestamp?.let { dataStore.saveLastUpdatedTimestamp(resourceType, timestamp) }
          }
        },
    )

  override suspend fun doWork(): Result {
    // Cache resources that might be needed urgent before sync
    ContentCache.saveResources(engine)

    return withContext(dispatcherProvider.singleThread()) { super.doWork() }
  }

  override fun getUploadStrategy(): UploadStrategy {
    val strategy =
      SyncUploadStrategy.valueOf(
        preference.read(
          SharedPreferenceKey.SYNC_UPLOAD_STRATEGY.name,
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
