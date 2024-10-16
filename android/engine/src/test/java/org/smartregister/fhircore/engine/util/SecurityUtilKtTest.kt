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

package org.smartregister.fhircore.engine.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class SecurityUtilKtTest {

  @Test
  fun testCharToPasswordHashGeneratesHash() {
    val secretPassword = "MySecretPassword"
    val hashedPassword =
      secretPassword.toCharArray().toPasswordHash(byteArrayOf(102, 103, 105, 107))
    assertNotNull(hashedPassword)
    assertNotEquals(secretPassword, hashedPassword)
  }

  @Test
  fun testPasswordHashStringGeneratesHash() {
    val secretPassword = "MySecretPassword"
    val hashedPassword =
      secretPassword.toCharArray().toPasswordHash(byteArrayOf(102, 103, 105, 107))
    assertNotNull(hashedPassword)
    assertNotEquals(secretPassword, hashedPassword)
  }

  @Test
  fun testGetRandomBytesOfSizeGeneratesRandomByteArray() {
    val firstBytes = 5.getRandomBytesOfSize()
    assertNotNull(firstBytes)
    assertEquals(5, firstBytes.size)

    val secondBytes = 5.getRandomBytesOfSize()
    assertNotNull(secondBytes)
    assertEquals(5, firstBytes.size)

    assertNotEquals(firstBytes, secondBytes)
  }
}
