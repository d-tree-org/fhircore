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

package org.smartregister.fhircore.engine.data.local.syncStrategy

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class SyncStrategyCacheDao {

  suspend fun upsert(logicalId: String) =
    with(get(logicalId)) {
      if (this == null) {
        insert(logicalId.toEntity())
      } else {
        update(this.logicalId)
      }
    }

  suspend fun upsert(logicalIds: List<String>) =
    logicalIds.onEach { logicalId ->
      with(get(logicalId)) {
        if (this == null) {
          insert(logicalId.toEntity())
        } else {
          update(this.logicalId)
        }
      }
    }

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insert(syncStrategyCacheEntity: List<SyncStrategyCacheEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insert(syncStrategyCacheEntity: SyncStrategyCacheEntity)

  @Query("DELETE FROM syncstrategycacheentity") abstract suspend fun deleteAll()

  @Query("SELECT * FROM syncstrategycacheentity WHERE shouldSync = 0")
  abstract suspend fun query(): List<SyncStrategyCacheEntity>

  @Query("SELECT * FROM syncstrategycacheentity WHERE logicalId = :logicalId")
  abstract suspend fun get(logicalId: String): SyncStrategyCacheEntity?

  @Query("UPDATE syncstrategycacheentity SET shouldSync = 1 WHERE logicalId = :logicalId")
  abstract suspend fun update(logicalId: String)

  @Query("UPDATE syncstrategycacheentity SET shouldSync = 0") abstract suspend fun resetAll()

  @Delete abstract suspend fun delete(syncStrategyCacheEntity: SyncStrategyCacheEntity)
}
