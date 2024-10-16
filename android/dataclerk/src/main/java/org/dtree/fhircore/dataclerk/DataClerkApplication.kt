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

package org.dtree.fhircore.dataclerk

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.fhir.datacapture.DataCaptureConfig
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirXFhirQueryResolver
import org.smartregister.fhircore.engine.data.remote.fhir.resource.ReferenceUrlResolver
import org.smartregister.fhircore.engine.trace.ReleaseTree
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireItemViewHolderFactoryMatchersProviderFactoryImpl
import org.smartregister.fhircore.engine.ui.questionnaire.items.CustomQuestItemDataProvider
import timber.log.Timber

@HiltAndroidApp
class DataClerkApplication : Application(), DataCaptureConfig.Provider, Configuration.Provider {
  private var configuration: DataCaptureConfig? = null

  @Inject lateinit var workerFactory: HiltWorkerFactory

  @Inject lateinit var referenceUrlResolver: ReferenceUrlResolver

  @Inject lateinit var xFhirQueryResolver: FhirXFhirQueryResolver

  @Inject lateinit var customQuestItemDataProvider: CustomQuestItemDataProvider

  override fun onCreate() {
    super.onCreate()

    if (BuildConfig.DEBUG) {
      Firebase.performance.isPerformanceCollectionEnabled = false
      Firebase.crashlytics.setCrashlyticsCollectionEnabled(false)
      Firebase.analytics.setAnalyticsCollectionEnabled(false)
      Timber.plant(Timber.DebugTree())
    } else {
      Timber.plant(ReleaseTree())
    }
  }

  override fun getDataCaptureConfig(): DataCaptureConfig {
    configuration =
      configuration
        ?: DataCaptureConfig(
          urlResolver = referenceUrlResolver,
          xFhirQueryResolver = xFhirQueryResolver,
          questionnaireItemViewHolderFactoryMatchersProviderFactory =
            QuestionnaireItemViewHolderFactoryMatchersProviderFactoryImpl(
              customQuestItemDataProvider,
            ),
        )
    return configuration as DataCaptureConfig
  }

  override val workManagerConfiguration: Configuration
    get() =
      Configuration.Builder()
        .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.VERBOSE else Log.INFO)
        .setWorkerFactory(workerFactory)
        .build()
}
