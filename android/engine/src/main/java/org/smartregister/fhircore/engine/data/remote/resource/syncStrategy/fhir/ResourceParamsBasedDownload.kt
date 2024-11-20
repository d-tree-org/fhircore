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

package org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.fhir

import com.google.android.fhir.sync.DownloadWorkManager
import com.google.android.fhir.sync.ParamMap
import com.google.android.fhir.sync.SyncDataParams
import com.google.android.fhir.sync.download.DownloadRequest
import java.net.URLEncoder
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import org.hl7.fhir.exceptions.FHIRException
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType

typealias ResourceSearchParams = Map<ResourceType, ParamMap>

class ResourceParamsBasedDownload(
  syncParams: ResourceSearchParams,
  val context: TimestampContext,
) : DownloadWorkManager {
  private val resourcesToDownloadWithSearchParams = LinkedList(syncParams.entries)
  private val urlOfTheNextPagesToDownloadForAResource = LinkedList<String>()

  override suspend fun getNextRequest(): DownloadRequest? {
    if (urlOfTheNextPagesToDownloadForAResource.isNotEmpty()) {
      return urlOfTheNextPagesToDownloadForAResource.poll()?.let { DownloadRequest.of(it) }
    }

    return resourcesToDownloadWithSearchParams.poll()?.let { (resourceType, params) ->
      val newParams =
        params.toMutableMap().apply { putAll(getLastUpdatedParam(resourceType, params, context)) }
      DownloadRequest.of("${resourceType.name}?${newParams.concatParams()}")
    }
  }

  private fun ParamMap.concatParams(): String {
    return this.entries.joinToString("&") { (key, value) ->
      val keyValue = if (key.contains("_tag")) "_tag" else key
      "$keyValue=${URLEncoder.encode(value, "UTF-8")}"
    }
  }

  /**
   * Returns the map of resourceType and URL for summary of total count for each download request
   */
  override suspend fun getSummaryRequestUrls(): Map<ResourceType, String> {
    return resourcesToDownloadWithSearchParams.associate { (resourceType, params) ->
      val newParams =
        params.toMutableMap().apply {
          putAll(getLastUpdatedParam(resourceType, params, context))
          putAll(getSummaryParam(params))
        }

      resourceType to "${resourceType.name}?${newParams.concatParams()}"
    }
  }

  private suspend fun getLastUpdatedParam(
    resourceType: ResourceType,
    params: ParamMap,
    context: TimestampContext,
  ): MutableMap<String, String> {
    val newParams = mutableMapOf<String, String>()
    if (!params.containsKey(SyncDataParams.SORT_KEY)) {
      newParams[SyncDataParams.SORT_KEY] = SyncDataParams.LAST_UPDATED_KEY
    }
    if (!params.containsKey(SyncDataParams.LAST_UPDATED_KEY)) {
      val lastUpdate = context.getLasUpdateTimestamp(resourceType)
      if (!lastUpdate.isNullOrEmpty()) {
        newParams[SyncDataParams.LAST_UPDATED_KEY] = "gt$lastUpdate"
      }
    }
    return newParams
  }

  private fun getSummaryParam(params: ParamMap): MutableMap<String, String> {
    val newParams = mutableMapOf<String, String>()
    if (!params.containsKey(SyncDataParams.SUMMARY_KEY)) {
      newParams[SyncDataParams.SUMMARY_KEY] = SyncDataParams.SUMMARY_COUNT_VALUE
    }
    return newParams
  }

  override suspend fun processResponse(response: Resource): Collection<Resource> {
    if (response is OperationOutcome) {
      throw FHIRException(response.issueFirstRep.diagnostics)
    }

    if ((response !is Bundle || response.type != Bundle.BundleType.SEARCHSET)) {
      return emptyList()
    }

    response.link
      .firstOrNull { component -> component.relation == "next" }
      ?.url
      ?.let { next -> urlOfTheNextPagesToDownloadForAResource.add(next) }

    return response.entry
      .map { it.resource }
      .also { resources ->
        resources
          .groupBy { it.resourceType }
          .entries
          .map { map ->
            map.value
              .filter { it.meta.lastUpdated != null }
              .let {
                context.saveLastUpdatedTimestamp(
                  map.key,
                  it.maxOfOrNull { it.meta.lastUpdated }?.toTimeZoneString(),
                )
              }
          }
      }
  }
}

interface TimestampContext {
  suspend fun saveLastUpdatedTimestamp(resourceType: ResourceType, timestamp: String?)

  suspend fun getLasUpdateTimestamp(resourceType: ResourceType): String?
}

private fun Date.toTimeZoneString(): String {
  val simpleDateFormat =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
      .withZone(ZoneId.systemDefault())
  return simpleDateFormat.format(this.toInstant())
}
