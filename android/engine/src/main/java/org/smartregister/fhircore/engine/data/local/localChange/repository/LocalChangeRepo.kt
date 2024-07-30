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

package org.smartregister.fhircore.engine.data.local.localChange.repository

import kotlinx.coroutines.flow.Flow
import org.smartregister.fhircore.engine.data.local.localChange.LocalChangeEntity
import org.smartregister.fhircore.engine.data.local.localChange.LocalChangeStateEvent

interface LocalChangeRepo {
  suspend fun queryFHIRLocalChanges()

  suspend fun deleteAll()

  fun query(): Flow<List<LocalChangeEntity>>

  suspend fun get(): List<LocalChangeEntity>

  suspend fun upsert(data: List<LocalChangeEntity>)

  operator fun invoke(localChange: LocalChangeEntity): Flow<LocalChangeStateEvent>
}
