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

package org.smartregister.fhircore.engine.util.extension

import android.database.SQLException
import ca.uhn.fhir.util.UrlUtil
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.SearchResult
import com.google.android.fhir.datacapture.extensions.logicalId
import com.google.android.fhir.db.ResourceNotFoundException
import com.google.android.fhir.get
import com.google.android.fhir.search.Operation
import com.google.android.fhir.search.Search
import com.google.android.fhir.search.SearchQuery
import com.google.android.fhir.search.filter.TokenParamFilterCriterion
import com.google.android.fhir.search.search
import com.google.android.fhir.workflow.FhirOperator
import org.hl7.fhir.r4.model.Composition
import org.hl7.fhir.r4.model.IdType
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Library
import org.hl7.fhir.r4.model.Measure
import org.hl7.fhir.r4.model.RelatedArtifact
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.Task
import org.smartregister.fhircore.engine.domain.util.PaginationConstant
import timber.log.Timber

suspend inline fun <reified T : Resource> FhirEngine.loadResource(resourceId: String): T? {
  return try {
    this.get(resourceId)
  } catch (resourceNotFoundException: ResourceNotFoundException) {
    null
  }
}

suspend fun FhirEngine.searchCompositionByIdentifier(identifier: String): Composition? =
  this.search<Composition> {
      filter(Composition.IDENTIFIER, { value = of(Identifier().apply { value = identifier }) })
    }
    .map { it.resource }
    .firstOrNull()

suspend fun FhirEngine.loadLibraryAtPath(fhirOperator: FhirOperator, path: String) {
  // resource path could be Library/123 OR something like http://fhir.labs.common/Library/123
  val library =
    runCatching { get<Library>(IdType(path).idPart) }.getOrNull()
      ?: search<Library> { filter(Library.URL, { value = path }) }.map { it.resource }.firstOrNull()
}

suspend fun FhirEngine.loadLibraryAtPath(
  fhirOperator: FhirOperator,
  relatedArtifact: RelatedArtifact,
) {
  if (
    relatedArtifact.type.isIn(
      RelatedArtifact.RelatedArtifactType.COMPOSEDOF,
      RelatedArtifact.RelatedArtifactType.DEPENDSON,
    )
  ) {
    loadLibraryAtPath(fhirOperator, relatedArtifact.resource)
  }
}

suspend fun FhirEngine.loadCqlLibraryBundle(fhirOperator: FhirOperator, measurePath: String) =
  try {
    // resource path could be Measure/123 OR something like http://fhir.labs.common/Measure/123
    val measure: Measure? =
      if (UrlUtil.isValid(measurePath)) {
        search<Measure> { filter(Measure.URL, { value = measurePath }) }
          .map { it.resource }
          .firstOrNull()
      } else {
        get(measurePath)
      }

    measure?.apply {
      relatedArtifact.forEach { loadLibraryAtPath(fhirOperator, it) }
      library.map { it.value }.forEach { path -> loadLibraryAtPath(fhirOperator, path) }
    }
  } catch (exception: Exception) {
    Timber.e(exception)
  }

suspend fun FhirEngine.addDateTimeIndex() {
  try {
    search<Task> {
      SearchQuery(
        "CREATE INDEX IF NOT EXISTS `index_DateTimeIndexEntity_index_from` ON `DateTimeIndexEntity` (`index_from`)",
        emptyList(),
      )
    }
  } catch (ex: SQLException) {
    Timber.e(ex)
  }
}

suspend inline fun <reified R : Resource> FhirEngine.getResourcesByIds(
  list: List<String>,
): List<R> {
  if (list.isEmpty()) return listOf()
  val paramQueries: List<(TokenParamFilterCriterion.() -> Unit)> =
    list.map { id -> { value = of(id) } }
  return this.search<R> {
      filter(Resource.RES_ID, *paramQueries.toTypedArray(), operation = Operation.OR)
    }
    .map { it.resource }
}

suspend fun FhirEngine.forceTagsUpdate(source: Resource) {
  try {
    var resource = source
    /** Increment [Resource.meta] versionId of [source]. */
    resource.meta.versionId?.toInt()?.plus(1)?.let {
      /** Assign [Resource.meta] versionId of [source]. */
      resource = resource.copy().apply { meta.versionId = "$it" }
      /** Delete a FHIR [source] in the local storage. */
      this.purge(resource.resourceType, resource.logicalId, forcePurge = true)
      /** Recreate a FHIR [source] in the local storage. */
      this.create(resource)
    }
  } catch (e: Exception) {
    Timber.e(e)
  }
}

suspend inline fun <reified T : Resource> FhirEngine.fetch(
  offset: Int = 0,
  loadAll: Boolean = true,
  block: Search.() -> Unit,
): List<SearchResult<T>> {
  if (!loadAll) {
    return this.search<T> {
      from = offset
      count = PaginationConstant.DEFAULT_PAGE_SIZE + PaginationConstant.EXTRA_ITEM_COUNT
      block()
    }
  }

  val resourcesList = mutableListOf<SearchResult<T>>()
  var currentOffset = offset
  val pageCount = 100

  do {
    val resources =
      this.search<T> {
        from = currentOffset
        count = pageCount
        block()
      }
    currentOffset += resources.size
    resourcesList.addAll(resources)
  } while (resources.isNotEmpty())

  return resourcesList
}
