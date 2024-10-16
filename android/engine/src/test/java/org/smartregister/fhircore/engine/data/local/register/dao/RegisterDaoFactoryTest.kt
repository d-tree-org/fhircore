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

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.smartregister.fhircore.engine.appfeature.model.HealthModule
import org.smartregister.fhircore.engine.robolectric.RobolectricTest

@HiltAndroidTest
class RegisterDaoFactoryTest : RobolectricTest() {

  @get:Rule val hiltRule = HiltAndroidRule(this)

  @Inject lateinit var registerDaoFactory: RegisterDaoFactory

  @Before
  fun setup() {
    hiltRule.inject()
  }

  @Test
  fun testRegisterDaoMap() {
    val registerDaoMap = registerDaoFactory.registerDaoMap
    Assert.assertNotNull(registerDaoMap)
    Assert.assertEquals(7, registerDaoMap.size)
    Assert.assertEquals(true, registerDaoMap.containsKey(HealthModule.ANC))
    Assert.assertEquals(true, registerDaoMap.containsKey(HealthModule.FAMILY))
    Assert.assertEquals(true, registerDaoMap.containsKey(HealthModule.HIV))
    Assert.assertEquals(true, registerDaoMap.containsKey(HealthModule.HOME_TRACING))
    Assert.assertEquals(true, registerDaoMap.containsKey(HealthModule.PHONE_TRACING))
    Assert.assertEquals(true, registerDaoMap.containsKey(HealthModule.APPOINTMENT))
    Assert.assertEquals(true, registerDaoMap.containsKey(HealthModule.DEFAULT))
  }
}
