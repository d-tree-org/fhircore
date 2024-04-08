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

package org.smartregister.fhircore.engine.ui.appsetting

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.mockk.spyk
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.smartregister.fhircore.engine.R
import org.smartregister.fhircore.engine.domain.util.DataLoadState
import org.smartregister.fhircore.engine.robolectric.RobolectricTest

class AppSettingScreenKtTest : RobolectricTest() {

  private class Listeners {
    val fetchConfiguration: (Context) -> Unit = spyk()

    val onAppIdChanged: (String) -> Unit = spyk()
  }

  private val appId = "appId"

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun testAppSettingScreenLayout() {
    composeRule.setContent {
      AppSettingScreen(
        goToHome = {},
        retry = {},
        state = DataLoadState.Loading,
      )
    }

    composeRule
      .onNodeWithText(context.getString(com.google.android.fhir.R.string.app_name))
      .assertExists()
  }

  @Test
  fun testErrorString() {
    var retried = false
    composeRule.setContent {
      AppSettingScreen(
        goToHome = {},
        retry = { retried = true },
        state = DataLoadState.Error(InternetConnectionException()),
      )
    }

    composeRule.onNodeWithText("Retry").assertHasClickAction()

    composeRule.onNodeWithText("Retry").performClick()

    Assert.assertTrue(retried)
  }
}
