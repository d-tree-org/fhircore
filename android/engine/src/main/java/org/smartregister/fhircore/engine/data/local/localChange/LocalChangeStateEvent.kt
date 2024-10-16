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

sealed interface LocalChangeStateEvent {
  data object Idle : LocalChangeStateEvent

  data object Finished : LocalChangeStateEvent

  data class Processing(val localChange: LocalChangeEntity) : LocalChangeStateEvent

  data class Completed(val localChange: LocalChangeEntity) : LocalChangeStateEvent

  data class Failed(val p0: LocalChangeEntity, val p1: Exception) : LocalChangeStateEvent
}
