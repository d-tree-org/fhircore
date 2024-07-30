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

package org.smartregister.fhircore.engine.data.local.localChange

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.android.fhir.LocalChange

@Entity
data class LocalChangeEntity(
  val resourceId: String,
  val resourceType: String,
  val versionId: String? = null,
  val type: String,
  @PrimaryKey(autoGenerate = false) val payload: String,
  val status: Int = 0,
) {
  enum class Type(val value: Int) {
    INSERT(1), // create a new resource. payload is the entire resource json.
    UPDATE(2), // patch. payload is the json patch.
    DELETE(3), // delete. payload is empty string.
    ;

    companion object {
      fun from(input: String): Type = entries.first { it.name == input }

      fun from(input: Int): Type = entries.first { it.value == input }
    }
  }
}

fun LocalChange.toEntity(): LocalChangeEntity {
  return LocalChangeEntity(resourceId, resourceType, versionId, type.name, payload)
}
