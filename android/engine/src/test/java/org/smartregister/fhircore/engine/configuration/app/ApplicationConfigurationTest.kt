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

package org.smartregister.fhircore.engine.configuration.app

import org.junit.Assert
import org.junit.Test

class ApplicationConfigurationTest {

  @Test
  fun testApplicationConfiguration() {
    val applicationConfiguration =
      ApplicationConfiguration(
        theme = "dark theme",
        languages = listOf("en"),
        syncInterval = 15,
        applicationName = "Test App",
        appLogoIconResourceFile = "ic_launcher",
        patientTypeFilterTagViaMetaCodingSystem = "test-filter-tag",
        count = "100",
        taskFilterTagViaMetaCodingSystem = "task-filter-tag",
      )
    Assert.assertEquals("dark theme", applicationConfiguration.theme)
    Assert.assertEquals(15, applicationConfiguration.syncInterval)
    Assert.assertEquals("Test App", applicationConfiguration.applicationName)
    Assert.assertEquals("ic_launcher", applicationConfiguration.appLogoIconResourceFile)
    Assert.assertEquals(
      "test-filter-tag",
      applicationConfiguration.patientTypeFilterTagViaMetaCodingSystem,
    )
    Assert.assertEquals("100", applicationConfiguration.count)
    Assert.assertEquals(
      "task-filter-tag",
      applicationConfiguration.taskFilterTagViaMetaCodingSystem,
    )
  }

  @Test
  fun testApplicationConfigurationOf() {
    val applicationConfiguration =
      applicationConfigurationOf(
        theme = "dark theme",
        languages = listOf("en"),
        syncInterval = 15,
        applicationName = "Test App",
        appLogoIconResourceFile = "ic_launcher",
        patientTypeFilterTagViaMetaCodingSystem = "test-filter-tag",
        count = "100",
        taskFilterTagViaMetaCodingSystem = "task-filter-tag",
      )
    Assert.assertEquals("dark theme", applicationConfiguration.theme)
    Assert.assertEquals(15, applicationConfiguration.syncInterval)
    Assert.assertEquals("Test App", applicationConfiguration.applicationName)
    Assert.assertEquals("ic_launcher", applicationConfiguration.appLogoIconResourceFile)
    Assert.assertEquals(
      "test-filter-tag",
      applicationConfiguration.patientTypeFilterTagViaMetaCodingSystem,
    )
    Assert.assertEquals("100", applicationConfiguration.count)
    Assert.assertEquals(
      "task-filter-tag",
      applicationConfiguration.taskFilterTagViaMetaCodingSystem,
    )
  }
}
