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

package org.smartregister.fhircore.engine.data.local.register.dao

import com.google.android.fhir.FhirEngine
import javax.inject.Inject
import javax.inject.Singleton
import org.hl7.fhir.r4.model.Coding
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.data.local.DefaultRepository
import org.smartregister.fhircore.engine.data.local.tracing.TracingRepository
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.ReasonConstants.phoneTracingCoding
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper

@Singleton
class PhoneTracingRegisterDao
@Inject
constructor(
  fhirEngine: FhirEngine,
  defaultRepository: DefaultRepository,
  tracingRepository: TracingRepository,
  configurationRegistry: ConfigurationRegistry,
  dispatcherProvider: DispatcherProvider,
  sharedPreferencesHelper: SharedPreferencesHelper,
) :
  TracingRegisterDao(
    fhirEngine,
    defaultRepository,
    tracingRepository,
    configurationRegistry,
    dispatcherProvider,
    sharedPreferencesHelper,
  ) {

  override val tracingCoding: Coding = phoneTracingCoding
}
