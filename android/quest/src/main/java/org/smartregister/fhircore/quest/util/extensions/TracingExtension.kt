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

package org.smartregister.fhircore.quest.util.extensions

import org.hl7.fhir.r4.model.Task
import org.smartregister.fhircore.engine.data.local.register.dao.HomeTracingRegisterDao
import org.smartregister.fhircore.engine.data.local.register.dao.PhoneTracingRegisterDao

fun Task.isHomeTracingTask(): Boolean {
  return this.meta.tag.firstOrNull {
    it.`is`(HomeTracingRegisterDao.taskCode.system, HomeTracingRegisterDao.taskCode.code)
  } !== null
}

fun Task.isPhoneTracingTask(): Boolean {
  return this.meta.tag.firstOrNull {
    it.`is`(PhoneTracingRegisterDao.taskCode.system, PhoneTracingRegisterDao.taskCode.code)
  } !== null
}
