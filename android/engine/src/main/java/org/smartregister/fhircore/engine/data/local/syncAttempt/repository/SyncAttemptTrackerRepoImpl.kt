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

package org.smartregister.fhircore.engine.data.local.syncAttempt.repository

import com.google.android.fhir.sync.SyncJobStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.smartregister.fhircore.engine.data.local.TingatheDatabase
import org.smartregister.fhircore.engine.data.local.localChange.LocalChangeStateEvent
import org.smartregister.fhircore.engine.data.local.localChange.repository.LocalChangeRepo
import org.smartregister.fhircore.engine.data.local.syncAttempt.SyncAttemptTrackerEntity

@Singleton
class SyncAttemptTrackerRepoImpl
@Inject
constructor(
  tingatheDatabase: TingatheDatabase,
  private val localChangeRepo: LocalChangeRepo,
) : SyncAttemptTrackerRepo {

  private var onSyncFailureJob: Job? = null

  private val syncAttemptTrackerDao = tingatheDatabase.syncAttemptTrackerDao
  private val channel = Channel<LocalChangeStateEvent>()

  override fun query(): Flow<List<SyncAttemptTrackerEntity>> {
    return syncAttemptTrackerDao.query()
  }

  override suspend fun upsert(syncAttemptTrackerEntity: SyncAttemptTrackerEntity) {
    syncAttemptTrackerDao.upsert(syncAttemptTrackerEntity)
  }

  override suspend fun get(): SyncAttemptTrackerEntity? {
    return syncAttemptTrackerDao.get()
  }

  override suspend fun deleteAll() {
    syncAttemptTrackerDao.deleteAll()
  }

  override val localChangeChannel: Channel<LocalChangeStateEvent>
    get() = channel

  private suspend fun onSyncFailure() =
    withContext(Dispatchers.IO) {
      localChangeRepo.deleteAll()
      localChangeRepo.queryFHIRLocalChanges()
      with(localChangeRepo.get()) getLocalChangeRepo@{
        forEachIndexed { index, value ->
          localChangeRepo
            .invoke(index, value)
            .onEach { event -> localChangeChannel.send(event.event) }
            .launchIn(this@withContext)
          if (index == this@getLocalChangeRepo.size.minus(1)) {
            deleteAll()
            localChangeRepo.deleteAll()
            localChangeChannel.send(LocalChangeStateEvent.Finished)
          }
        }
      }
    }

  override suspend fun invoke(syncJobStatus: SyncJobStatus) {
    withContext(Dispatchers.IO) {
      when (syncJobStatus) {
        is SyncJobStatus.Failed ->
          with(get() ?: SyncAttemptTrackerEntity()) {
            copy(attempts = attempts.plus(1)).also { upsert(syncAttemptTrackerEntity = it) }
          }
        is SyncJobStatus.Succeeded -> {
          with(get() ?: SyncAttemptTrackerEntity()) {
            upsert(syncAttemptTrackerEntity = copy(attempts = 0))
          }
        }
        else -> Unit
      }
    }
  }
}
