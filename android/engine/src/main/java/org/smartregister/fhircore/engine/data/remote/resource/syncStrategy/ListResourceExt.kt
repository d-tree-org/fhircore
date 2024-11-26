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

package org.smartregister.fhircore.engine.data.remote.resource.syncStrategy

import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.Search
import org.hl7.fhir.r4.model.ListResource
import org.hl7.fhir.r4.model.ResourceType

suspend fun getListResource(fhirEngine: FhirEngine) =
  fhirEngine
    .search<ListResource>(
      Search(ResourceType.List).apply {
        filter(ListResource.TITLE, { value = "Patient Identifier List" })
      },
    )
    .map { it.resource }
    .firstOrNull()

suspend fun getIdentifiers(fhirEngine: FhirEngine): ListResourceItem? {
  val listResource: ListResource = getListResource(fhirEngine) ?: return null
  return ListResourceItem(
    data = listResource.entry.mapNotNull { entry -> entry.item.display.toInt() }.toList(),
    idPart = listResource.idPart,
  )
}

data class ListResourceItem(
  val data: List<Int>,
  val idPart: String,
)
