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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import org.smartregister.fhircore.engine.data.local.localChange.LocalChangeStateEvent
import org.smartregister.fhircore.engine.data.local.syncAttempt.SyncAttemptTrackerEntity

interface SyncAttemptTrackerRepo {
  fun query(): Flow<List<SyncAttemptTrackerEntity>>

  suspend operator fun invoke(syncJobStatus: SyncJobStatus)

  suspend fun upsert(syncAttemptTrackerEntity: SyncAttemptTrackerEntity)

  suspend fun get(): SyncAttemptTrackerEntity?

  suspend fun deleteAll()

  val localChangeChannel: Channel<LocalChangeStateEvent>
}
