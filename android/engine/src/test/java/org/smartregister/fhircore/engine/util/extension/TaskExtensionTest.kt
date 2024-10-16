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

package org.smartregister.fhircore.engine.util.extension

import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Task
import org.junit.Assert
import org.junit.Test

class TaskExtensionTest {
  private val testTask1 =
    Task().apply {
      this.id = "1"
      this.status = Task.TaskStatus.READY
      this.meta.addTag(
        Coding().apply {
          system = "https://d-tree.org"
          code = "clinic-visit-task-order-3"
        },
      )
      this.meta.addTag(
        Coding().apply {
          system = "https://d-tree.org"
          code = "guardian-visit"
        },
      )
      this.reasonReference = Reference("Questionnaire/testtask1")
    }

  private val testTask2 =
    Task().apply {
      this.id = "2"
      this.status = Task.TaskStatus.COMPLETED
      this.meta.addTag(
        Coding().apply {
          system = "https://d-tree.org"
          code = "clinic-visit-task-order-1"
        },
      )
      this.reasonReference = Reference("Questionnaire/testtask2")
    }

  @Test
  fun `task isGuardianVisit`() {
    val systemTag = "https://d-tree.org"
    Assert.assertTrue(testTask1.isGuardianVisit(systemTag))
    Assert.assertFalse(testTask2.isGuardianVisit(systemTag))
  }

  @Test
  fun `task clinicVisitOrder`() {
    val systemTag = "https://d-tree.org"
    Assert.assertEquals(3.0, testTask1.clinicVisitOrder(systemTag))
    Assert.assertEquals(testTask2.clinicVisitOrder(systemTag), 1.0)
    val task = testTask1.copy().apply { meta.tag.first().code = systemTag }
    Assert.assertNull(task.clinicVisitOrder(systemTag))
  }

  @Test
  fun `task clinicVisitOrder null when clinic-visit-order code invalid`() {
    val systemTag = "https://d-tree.org"
    val task =
      Task().apply {
        meta.addTag(
          Coding().apply {
            system = systemTag
            code = "clinic-visit-task-order-NAN"
          },
        )
      }
    Assert.assertNull(task.clinicVisitOrder(systemTag))
  }

  @Test
  fun `task clinicVisitOrder when clinic-visit-order code is double`() {
    val systemTag = "https://d-tree.org"
    val task =
      Task().apply {
        meta.addTag(
          Coding().apply {
            system = systemTag
            code = "clinic-visit-task-order-34.2"
          },
        )
      }
    Assert.assertEquals(34.2, task.clinicVisitOrder(systemTag))
  }

  @Test
  fun `task clinicVisitOrder with when clinic-visit-order code mixed - and _`() {
    val systemTag = "https://d-tree.org"
    val task =
      Task().apply {
        meta.addTag(
          Coding().apply {
            system = systemTag
            code = "CLINIC_VISIT-TASK-ORDER_48.1"
          },
        )
      }
    Assert.assertEquals(48.1, task.clinicVisitOrder(systemTag))
  }

  @Test
  fun `task isNotCompleted`() {
    Assert.assertTrue(testTask1.isNotCompleted())
    Assert.assertFalse(testTask2.isNotCompleted())
  }

  @Test
  fun `task canBeCompleted`() {
    Assert.assertTrue(testTask1.canBeCompleted())
    Assert.assertFalse(testTask2.canBeCompleted())
  }
}
