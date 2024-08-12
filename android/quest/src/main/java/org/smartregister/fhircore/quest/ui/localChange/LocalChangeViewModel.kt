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

package org.smartregister.fhircore.quest.ui.localChange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.smartregister.fhircore.engine.data.local.localChange.LocalChangeStateEvent
import org.smartregister.fhircore.engine.data.local.localChange.repository.LocalChangeRepo
import org.smartregister.fhircore.engine.data.local.syncAttempt.repository.SyncAttemptTrackerRepo
import org.smartregister.fhircore.engine.sync.SyncBroadcaster

@HiltViewModel
class LocalChangeViewModel
@Inject
constructor(
  private val syncBroadcaster: SyncBroadcaster,
  private val localChangeRepo: LocalChangeRepo,
  private val syncAttemptTrackerRepo: SyncAttemptTrackerRepo,
) : ViewModel() {

  private val _state = MutableStateFlow(LocalChangeState())
  val state = _state.asStateFlow()

  private fun syncAttemptChannelFlow() =
    syncAttemptTrackerRepo.localChangeChannel
      .receiveAsFlow()
      .onEach { event -> _state.update { it.copy(event = event) } }
      .launchIn(viewModelScope)

  private fun listenToLocalChangeRepo() =
    localChangeRepo
      .query()
      .onEach { localChanges -> _state.update { it.copy(localChanges = localChanges) } }
      .launchIn(viewModelScope)

  private fun shouldShowLocalChangeScreen() =
    syncAttemptTrackerRepo
      .query()
      .onEach { syncAttemptEntity ->
        _state.update {
          it.copy(
            shouldShow =
              if (syncAttemptEntity.isEmpty()) {
                false
              } else {
                syncAttemptEntity.map { entity -> entity.attempts }.first() > 2
              },
          )
        }
      }
      .launchIn(viewModelScope)

  init {
    listenToLocalChangeRepo()
  }

  private fun onQuery() {
    viewModelScope.launch(Dispatchers.IO) {
      localChangeRepo.deleteAll()
      localChangeRepo.queryFHIRLocalChanges()
    }
  }

  private fun onBatch() =
    viewModelScope.launch(Dispatchers.IO) {
      with(localChangeRepo.get()) getLocalChangeRepo@{
        forEachIndexed { index, value ->
          localChangeRepo
            .invoke(index, value)
            .onEach { event ->
              _state.update { it.copy(event = event.event) }
              if (event.index.plus(1) == this@getLocalChangeRepo.size) {
                _state.update { it.copy(event = LocalChangeStateEvent.Finished) }
              }
            }
            .launchIn(this@launch)
        }
      }
    }

  private fun onCompleted() {
    viewModelScope.launch {
      syncBroadcaster.runSync()
      localChangeRepo.deleteAll()
    }
  }

  private fun onRetry() = _state.update { it.copy(retry = it.retry.plus(1)) }

  fun onEvent(event: LocalChangeEvent) {
    when (event) {
      is LocalChangeEvent.Query -> onQuery()
      LocalChangeEvent.Retry -> onRetry()
      LocalChangeEvent.Batch -> onBatch()
      LocalChangeEvent.Completed -> onCompleted()
    }
  }
}
