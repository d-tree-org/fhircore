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

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import ca.uhn.fhir.parser.IParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.Binary
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Resource
import org.smartregister.fhircore.engine.data.local.localChange.LocalChangeEntity.Type.Companion.from

val jsonParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
const val contentType = "application/json"

suspend fun bundleComponent(
  localChangeEntities: List<LocalChangeEntity>,
): List<Bundle.BundleEntryComponent> {
  return withContext(Dispatchers.IO) {
    val updateLocalChange =
      localChangeEntities
        .filter { from(it.type) == LocalChangeEntity.Type.UPDATE }
        .map { createRequest(it, createPathRequest(it)) }

    val insertDeleteLocalChange =
      localChangeEntities
        .filterNot { from(it.type) == LocalChangeEntity.Type.UPDATE }
        .sortedBy { it.versionId }
        .map { change ->
          createRequest(change, (jsonParser.parseResource(change.payload) as Resource))
        }
    insertDeleteLocalChange + updateLocalChange
  }
}

fun createPathRequest(localChangeEntity: LocalChangeEntity): Binary {
  return Binary().apply {
    contentType = "application/json-patch+json"
    data = localChangeEntity.payload.toByteArray()
  }
}

fun createRequest(
  localChange: LocalChangeEntity,
  resourceToUpload: Resource,
): Bundle.BundleEntryComponent {
  return Bundle.BundleEntryComponent().apply {
    resource = resourceToUpload
    request =
      Bundle.BundleEntryRequestComponent().apply {
        url = "${localChange.resourceType}/${localChange.resourceId}"
        method =
          when (from(localChange.type)) {
            LocalChangeEntity.Type.INSERT -> Bundle.HTTPVerb.PUT
            LocalChangeEntity.Type.UPDATE -> Bundle.HTTPVerb.PATCH
            LocalChangeEntity.Type.DELETE -> Bundle.HTTPVerb.DELETE
          }
      }
  }
}
