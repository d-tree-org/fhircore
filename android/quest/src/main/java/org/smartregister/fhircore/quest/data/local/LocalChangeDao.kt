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

package org.smartregister.fhircore.quest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LocalChangeDao {

  @Query("SELECT * FROM LocalChangeModel ORDER BY resourceType")
  abstract fun query(): Flow<List<LocalChangeModel>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun upsert(data: List<LocalChangeModel>)

  @Query("SELECT * FROM LocalChangeModel WHERE status != 2 ORDER BY type")
  abstract suspend fun get(): List<LocalChangeModel>

  @Query("DELETE FROM LocalChangeModel") abstract suspend fun clear(): Int
}
