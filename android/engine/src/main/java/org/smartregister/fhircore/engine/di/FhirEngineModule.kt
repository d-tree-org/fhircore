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

package org.smartregister.fhircore.engine.di

import android.content.Context
import com.google.android.fhir.DatabaseErrorStrategy
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.FhirEngineConfiguration
import com.google.android.fhir.FhirEngineProvider
import com.google.android.fhir.NetworkConfiguration
import com.google.android.fhir.ServerConfiguration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.smartregister.fhircore.engine.BuildConfig
import org.smartregister.fhircore.engine.configuration.app.ConfigService
import org.smartregister.fhircore.engine.data.remote.shared.TokenAuthenticator
import org.smartregister.fhircore.engine.di.NetworkModule.Companion.TIMEOUT_DURATION

/**
 * Provide [FhirEngine] dependency in isolation so we can replace it with a fake dependency in test
 */
@InstallIn(SingletonComponent::class)
@Module
class FhirEngineModule {

  @Singleton
  @Provides
  fun provideFhirEngine(
    @ApplicationContext context: Context,
    tokenAuthenticator: TokenAuthenticator,
    configService: ConfigService,
  ): FhirEngine {
    FhirEngineProvider.init(
      FhirEngineConfiguration(
        enableEncryptionIfSupported = !BuildConfig.DEBUG,
        DatabaseErrorStrategy.UNSPECIFIED,
        ServerConfiguration(
          baseUrl = configService.provideAuthConfiguration().fhirServerBaseUrl,
          authenticator = tokenAuthenticator,
          networkConfiguration =
            NetworkConfiguration(TIMEOUT_DURATION, TIMEOUT_DURATION, TIMEOUT_DURATION),
        ),
      ),
    )

    return FhirEngineProvider.getInstance(context)
  }
}
