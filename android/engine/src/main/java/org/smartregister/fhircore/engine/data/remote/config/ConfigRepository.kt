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

package org.smartregister.fhircore.engine.data.remote.config

import javax.inject.Inject
import org.hl7.fhir.r4.model.ResourceType
import org.smartregister.fhircore.engine.configuration.app.AppConfigService
import org.smartregister.fhircore.engine.data.local.DefaultRepository
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceDataSource

class ConfigRepository
@Inject
constructor(
  private val appConfigService: AppConfigService,
  private val fhirResourceDataSource: FhirResourceDataSource,
  private val defaultRepository: DefaultRepository,
) {
  suspend fun fetchConfigFromRemote() {
    val binaryResources =
      fhirResourceDataSource
        .queryByResourceId(
          ResourceType.Binary.name,
          appConfigService.getAppId(),
        )
        .entry
        .map { it.resource }

    if (binaryResources.isEmpty()) throw Exception("${appConfigService.getAppId()} not found")

    defaultRepository.saveLocalOnly(*binaryResources.toTypedArray())
  }
}
