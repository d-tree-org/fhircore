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

package org.smartregister.fhircore.engine.domain.repository

import kotlinx.coroutines.flow.Flow
import org.smartregister.fhircore.engine.appfeature.model.HealthModule
import org.smartregister.fhircore.engine.data.local.RegisterFilter
import org.smartregister.fhircore.engine.domain.model.ProfileData
import org.smartregister.fhircore.engine.domain.model.RegisterData

/** Common repository for register. */
interface RegisterRepository {
  suspend fun loadRegisterData(
    currentPage: Int,
    loadAll: Boolean = false,
    appFeatureName: String? = null,
    healthModule: HealthModule = HealthModule.DEFAULT,
    patientSearchText: String? = null,
  ): List<RegisterData>

  suspend fun loadRegisterFiltered(
    currentPage: Int,
    loadAll: Boolean = false,
    healthModule: HealthModule,
    filters: RegisterFilter,
    patientSearchText: String? = null,
  ): List<RegisterData>

  suspend fun countRegisterFiltered(
    healthModule: HealthModule,
    filters: RegisterFilter,
  ): Long

  suspend fun countRegisterData(
    healthModule: HealthModule = HealthModule.DEFAULT,
  ): Flow<Long>

  suspend fun loadPatientProfileData(
    appFeatureName: String? = null,
    healthModule: HealthModule = HealthModule.DEFAULT,
    patientId: String,
  ): ProfileData?

  suspend fun searchByName(
    nameQuery: String,
    currentPage: Int,
    appFeatureName: String?,
    healthModule: HealthModule,
  ): List<RegisterData>
}
