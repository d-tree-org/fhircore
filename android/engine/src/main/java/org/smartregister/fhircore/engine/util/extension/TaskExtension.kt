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

import org.hl7.fhir.r4.model.CarePlan
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Task
import org.smartregister.fhircore.engine.util.DateUtils
import org.smartregister.fhircore.engine.util.DateUtils.isToday
import org.smartregister.fhircore.engine.util.DateUtils.today
import org.smartregister.fhircore.engine.util.SystemConstants

const val GUARDIAN_VISIT_CODE = "guardian-visit"
const val CLINICAL_VISIT_ORDER_CODE_REGEX_FORMAT = "clinic-visit-task-order-\\d*\\.?\\d*\$"

fun Task.hasPastEnd() =
  this.hasExecutionPeriod() &&
    this.executionPeriod.hasEnd() &&
    this.executionPeriod.end.before(DateUtils.yesterday())

fun Task.hasStarted() =
  this.hasExecutionPeriod() &&
    this.executionPeriod.hasStart() &&
    with(this.executionPeriod.start) { this.before(today()) || this.isToday() }

fun Task.TaskStatus.toCoding() = Coding(this.system, this.toCode(), this.display)

fun Task.clinicVisitOrder(systemTag: String): Double? =
  this.meta.tag
    .asSequence()
    .filter { it.system.equals(systemTag, true) }
    .filter { !it.code.isNullOrBlank() }
    .map { it.code.replace("_", "-") }
    .filter {
      it.matches(Regex(CLINICAL_VISIT_ORDER_CODE_REGEX_FORMAT, option = RegexOption.IGNORE_CASE))
    }
    .map { it.substringAfterLast("-").trim() }
    .map { it.toDoubleOrNull() }
    .lastOrNull()

fun Task.isNotCompleted() = this.status != Task.TaskStatus.COMPLETED

fun Task.canBeCompleted() = this.hasReasonReference().and(this.isNotCompleted())

fun Task.extractedTracingCategoryIsPhone(filterTag: String): Boolean {
  val tagList =
    this.meta.tag.filter { it.system.equals(filterTag, true) }.filterNot { it.code.isNullOrBlank() }
  return if (filterTag.isEmpty() || tagList.isEmpty()) {
    false
  } else {
    tagList.last().code.equals("phone-tracing")
  }
}

fun Task.getCarePlanId(): String? {
  return meta.tag
    .firstOrNull { it.system == SystemConstants.CARE_PLAN_REFERENCE_SYSTEM }
    ?.code
    ?.substringAfterLast(delimiter = '/', missingDelimiterValue = "")
}

fun Task.taskStatusToCarePlanActivityStatus(): CarePlan.CarePlanActivityStatus {
  return when (status) {
    Task.TaskStatus.FAILED -> CarePlan.CarePlanActivityStatus.STOPPED
    Task.TaskStatus.CANCELLED -> CarePlan.CarePlanActivityStatus.CANCELLED
    Task.TaskStatus.READY -> CarePlan.CarePlanActivityStatus.NOTSTARTED
    Task.TaskStatus.COMPLETED,
    Task.TaskStatus.ONHOLD,
    Task.TaskStatus.INPROGRESS,
    Task.TaskStatus.ENTEREDINERROR, -> CarePlan.CarePlanActivityStatus.fromCode(status.toCode())
    else -> CarePlan.CarePlanActivityStatus.NULL
  }
}
