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
import com.google.android.fhir.sync.download.DownloadRequest
import java.util.LinkedList
import org.hl7.fhir.exceptions.FHIRException
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType

class IdentifierSyncParams(
  private val identifiers: List<Int>,
  private val callback: (ParamSyncStatus) -> Unit,
) : DownloadWorkManager {

  private val urlOfTheNextPagesToDownloadForAResource = LinkedList<String>()
  private val resourcesToDownloadWithSearchParams = LinkedList(identifiers.chunked(32))
  private var patientPosition = 0

  override suspend fun getNextRequest(): DownloadRequest? {
    if (urlOfTheNextPagesToDownloadForAResource.isNotEmpty()) {
      return urlOfTheNextPagesToDownloadForAResource.poll()?.let { DownloadRequest.of(it) }
    }

    return resourcesToDownloadWithSearchParams.poll()?.let { ids ->
      DownloadRequest.of(bundle = ids.bundleOf().also { patientPosition += ids.size })
    }
  }

  override suspend fun processResponse(response: Resource): Collection<Resource> {
    if (response is OperationOutcome) {
      throw FHIRException(response.issueFirstRep.diagnostics)
    }

    if ((response !is Bundle || response.type != Bundle.BundleType.BATCHRESPONSE)) {
      return emptyList()
    }

    response.link
      .firstOrNull { component -> component.relation == "next" }
      ?.url
      ?.let { next -> urlOfTheNextPagesToDownloadForAResource.add(next) }

    return response.entry
      .mapNotNull { it.resource as Bundle }
      .map { it.entry.map { it.resource } }
      .flatten()
      .also(::catchIds)
  }

  private fun catchIds(resources: List<Resource>) =
    resources
      .filter { it.resourceType == ResourceType.Patient }
      .also { patients ->
        callback(
          ParamSyncStatus(
            logicalId = patients.map { it.idPart },
            idsTotal = identifiers.size,
            patientPositionAt = patientPosition,
          ),
        )
      }

  private fun List<Int>.bundleOf(): Bundle {
    return Bundle().apply {
      type = Bundle.BundleType.BATCH
      entry = bundleEntryComponent()
    }
  }

  private fun List<Int>.bundleEntryComponent(): List<Bundle.BundleEntryComponent> {
    return flatMap {
      listOf("Patient?identifier=$it").map { url ->
        Bundle.BundleEntryComponent().apply {
          request =
            Bundle.BundleEntryRequestComponent().apply {
              method = Bundle.HTTPVerb.GET
              this.url = url
            }
        }
      }
    }
  }

  override suspend fun getSummaryRequestUrls() = mapOf<ResourceType, String>()
}
