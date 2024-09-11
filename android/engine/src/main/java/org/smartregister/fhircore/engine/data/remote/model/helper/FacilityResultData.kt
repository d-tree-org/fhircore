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

package org.smartregister.fhircore.engine.data.remote.model.helper

import java.time.LocalDate
import java.time.LocalDateTime

data class FacilityResultData(
  val groups: List<GroupedSummaryItem>,
  val date: LocalDate,
  val generatedDate: LocalDateTime,
)

data class GroupedSummaryItem(
  val groupKey: String,
  val groupTitle: String,
  val summaries: List<SummaryItem>,
  val order: Int,
)

data class SummaryItem(val name: String, val value: Int)
