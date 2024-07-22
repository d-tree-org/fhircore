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

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.LocalChange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.ResourceType
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceService
import org.smartregister.fhircore.engine.sync.NetworkState
import org.smartregister.fhircore.engine.sync.SyncBroadcaster
import org.smartregister.fhircore.quest.data.local.LocalChangeModel
import org.smartregister.fhircore.quest.data.local.TingatheDatabase
import retrofit2.HttpException
import timber.log.Timber

@HiltViewModel
class LocalChangeViewModel
@Inject
constructor(
  private val tingatheDatabase: TingatheDatabase,
  private val api: FhirResourceService,
  private val fhirEngine: FhirEngine,
  private val syncBroadcaster: SyncBroadcaster,
  private val application: Application,
) : ViewModel() {

  private val _state = MutableStateFlow(LocalChangeState())
  val state = _state.asStateFlow()
  private val localChangeDao = tingatheDatabase.localChangeDao

  init {
    localChangeDao
      .query()
      .onEach { patch -> _state.update { it.copy(localChanges = patch) } }
      .shareIn(viewModelScope, SharingStarted.Lazily)
      .launchIn(viewModelScope)
  }

  private suspend fun getLocalChanges(localChange: LocalChange) =
    fhirEngine
      .getLocalChanges(
        ResourceType.fromCode(localChange.resourceType),
        localChange.resourceId,
      )
      .also { data -> localChangeDao.upsert(data.map { it.toEntity() }) }

  private fun onQuery() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        tingatheDatabase.withTransaction {
          localChangeDao.clear()
          fhirEngine.getUnsyncedLocalChanges().forEach { getLocalChanges(it) }
          _state.update { it.copy(retry = 0) }
        }
      } catch (e: Exception) {
        Timber.e(e)
      }
    }
  }

  private fun onBatch() =
    viewModelScope.launch(Dispatchers.IO) {
      with(localChangeDao.get()) {
        forEachIndexed { index, value ->
          onPatchLocalChange(
            index,
            size,
            value,
          )
        }
      }
    }

  private fun onSingle(event: LocalChangeEvent.Single) =
    viewModelScope.launch(Dispatchers.IO) { onPatchLocalChange(data = event.patch) }

  private suspend fun onPatchLocalChange(
    firstIndex: Int = 1,
    lastIndex: Int = 1,
    data: LocalChangeModel,
  ) {
    if (NetworkState(application).invoke()) {
      try {
        _state.update { it.copy(stateEvent = LocalChangeStateEvent.Processing) }
        localChangeDao.upsert(listOf(data.copy(status = 1)))
        val bundle =
          Bundle().apply {
            entry = bundleComponent(listOf(data))
            type = Bundle.BundleType.TRANSACTION
          }
        val mediaType = contentType.toMediaTypeOrNull()
        val requestBody = jsonParser.encodeResourceToString(bundle).toRequestBody(mediaType)
        api.post(requestBody)
        localChangeDao.upsert(listOf(data.copy(status = 2)))
      } catch (e: HttpException) {
        when {
          e.code() == 404 -> {}
        }
        Timber.e(e)
        localChangeDao.upsert(listOf(data.copy(status = 3)))
        _state.update { it.copy(stateEvent = LocalChangeStateEvent.Failed) }
      } catch (e: IOException) {
        _state.update { it.copy(stateEvent = LocalChangeStateEvent.Failed) }
        Timber.e(e)
      } catch (e: Exception) {
        Timber.e(e)
        _state.update { it.copy(stateEvent = LocalChangeStateEvent.Failed) }
      }
      if (firstIndex == lastIndex) {
        _state.update { it.copy(stateEvent = LocalChangeStateEvent.Finished) }
      }
    } else _state.update { it.copy(stateEvent = LocalChangeStateEvent.Failed) }
  }

  private fun onCompleted() {
    viewModelScope.launch {
      syncBroadcaster.runSync()
      localChangeDao.clear()
      _state.update { it.copy(retry = 0) }
    }
  }

  private fun onRetry() = _state.update { it.copy(retry = it.retry.plus(1)) }

  fun onEvent(event: LocalChangeEvent) {
    when (event) {
      is LocalChangeEvent.Query -> onQuery()
      is LocalChangeEvent.Single -> onSingle(event)
      LocalChangeEvent.Retry -> onRetry()
      LocalChangeEvent.Batch -> onBatch()
      LocalChangeEvent.Completed -> onCompleted()
    }
  }
}
