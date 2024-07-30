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

import com.google.android.fhir.FhirEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.ResourceType
import org.smartregister.fhircore.engine.data.local.TingatheDatabase
import org.smartregister.fhircore.engine.data.local.localChange.LocalChangeEntity
import org.smartregister.fhircore.engine.data.local.localChange.LocalChangeStateEvent
import org.smartregister.fhircore.engine.data.local.localChange.bundleComponent
import org.smartregister.fhircore.engine.data.local.localChange.contentType
import org.smartregister.fhircore.engine.data.local.localChange.jsonParser
import org.smartregister.fhircore.engine.data.local.localChange.toEntity
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceService

@Singleton
class LocalChangeRepoImpl
@Inject
constructor(
  private val database: TingatheDatabase,
  private val fhirEngine: FhirEngine,
  private val fhirResourceService: FhirResourceService,
) : LocalChangeRepo {

  override suspend fun queryFHIRLocalChanges() {
    fhirEngine.getUnsyncedLocalChanges().onEach { localChange ->
      fhirEngine
        .getLocalChanges(
          ResourceType.fromCode(localChange.resourceType),
          localChange.resourceId,
        )
        .also { upsert(it.map { it.toEntity() }) }
    }
  }

  override fun query() = database.localChangeDao.query()

  override suspend fun get() = database.localChangeDao.get()

  override suspend fun deleteAll() {
    database.localChangeDao.deleteAll()
  }

  override suspend fun upsert(data: List<LocalChangeEntity>) {
    database.localChangeDao.upsert(data)
  }

  private suspend fun onPutPost(localChange: LocalChangeEntity): LocalChangeStateEvent {
    localChange upsert 1
    return when (localChange.type) {
      LocalChangeEntity.Type.INSERT.name -> {
        try {
          fhirResourceService.insertResource(
            resourceType = localChange.resourceType,
            id = localChange.resourceId,
            body = localChange.payload.toRequestBody(contentType.toMediaTypeOrNull()),
          )
          LocalChangeStateEvent.Completed(localChange)
        } catch (exception: Exception) {
          exception.printStackTrace()
          LocalChangeStateEvent.Failed(localChange, exception)
        }
      }
      else -> {
        try {
          Bundle().apply {
            entry = bundleComponent(listOf(localChange))
            type = Bundle.BundleType.TRANSACTION
            val requestBody =
              jsonParser.encodeResourceToString(this).toRequestBody(contentType.toMediaTypeOrNull())
            fhirResourceService.post(requestBody)
          }
          LocalChangeStateEvent.Completed(localChange)
        } catch (exception: Exception) {
          exception.printStackTrace()
          LocalChangeStateEvent.Failed(localChange, exception)
        }
      }
    }
  }

  private suspend infix fun LocalChangeEntity.upsert(status: Int) =
    upsert(listOf(copy(status = status)))

  override operator fun invoke(localChange: LocalChangeEntity) = flow {
    with(onPutPost(localChange)) {
      when (this) {
        is LocalChangeStateEvent.Completed -> localChange upsert 2
        is LocalChangeStateEvent.Failed -> localChange upsert 3
        else -> Unit
      }
      emit(this)
    }
  }
}
