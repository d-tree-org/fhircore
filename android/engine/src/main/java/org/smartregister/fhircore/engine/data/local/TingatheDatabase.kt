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

package org.smartregister.fhircore.engine.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.smartregister.fhircore.engine.data.local.localChange.LocalChangeDao
import org.smartregister.fhircore.engine.data.local.localChange.LocalChangeEntity
import org.smartregister.fhircore.engine.data.local.syncAttempt.SyncAttemptTrackerDao
import org.smartregister.fhircore.engine.data.local.syncAttempt.SyncAttemptTrackerEntity

@Database(
  version = 2,
  entities =
    [
      LocalChangeEntity::class,
      SyncAttemptTrackerEntity::class,
    ],
)
abstract class TingatheDatabase : RoomDatabase() {

  abstract val localChangeDao: LocalChangeDao
  abstract val syncAttemptTrackerDao: SyncAttemptTrackerDao

  companion object {
    fun databaseBuilder(context: Context): Builder<TingatheDatabase> {
      return Room.databaseBuilder(context, TingatheDatabase::class.java, "tingathe_db")
    }
  }
}
