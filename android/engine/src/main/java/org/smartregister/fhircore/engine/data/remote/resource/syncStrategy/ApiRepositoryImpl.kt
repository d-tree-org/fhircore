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

package org.smartregister.fhircore.engine.data.remote.resource.syncStrategy

import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.ResourceType
import org.smartregister.fhircore.engine.R
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceService
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.repository.ApiRepository
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SearchBy
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SearchBy.HUMAN_NAME
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SearchBy.IDENTIFIER
import org.smartregister.fhircore.engine.util.AppDataStore
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper

class ApiRepositoryImpl(
  private val fhirResourceService: FhirResourceService,
  sharedPreferencesHelper: SharedPreferencesHelper,
) : ApiRepository() {

  private val context = sharedPreferencesHelper.context
  private val system = context.getString(R.string.sync_strategy_organization_system)
  private val tag = "$system%7C${sharedPreferencesHelper.organisationCode()}"

  override suspend fun search(searchQuery: String, criteria: SearchBy): List<Patient> =
    fhirResourceService
      .getResource(
        when (criteria) {
          IDENTIFIER -> "${ResourceType.Patient.name}?identifier=$searchQuery&_tag=$tag"
          HUMAN_NAME ->
            "${ResourceType.Patient.name}?given=$searchQuery,family=$searchQuery&_tag=$tag"
        },
      )
      .entry
      .map { it.resource as Patient }
}

private const val SYNC_TIMESTAMP_INPUT_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"

enum class OfType {
  Patient,
  Encounter,
  Observation,
  Condition,
  CarePlan,
  List,
  Task,
  Practitioner,
  RelatedPerson,
  Appointment,
}

fun simpleDateFormat() = SimpleDateFormat(SYNC_TIMESTAMP_INPUT_FORMAT, Locale.getDefault())

fun Date.toOffsetDateTime(): OffsetDateTime {
  return OffsetDateTime.ofInstant(toInstant(), ZoneId.systemDefault())
}

private fun OffsetDateTime.formatLastSyncTimestamp(): String {
  val syncTimestampFormatter =
    SimpleDateFormat(SYNC_TIMESTAMP_INPUT_FORMAT, Locale.getDefault()).apply {
      timeZone = TimeZone.getDefault()
    }
  val parse: Date? = syncTimestampFormatter.parse(toString())
  return if (parse == null) "" else simpleDateFormat().format(parse)
}

suspend fun saveLastUpdatedTimestamp(appDataStore: AppDataStore) {
  OfType.entries
    .map {
      when (it) {
        OfType.Patient -> ResourceType.Patient
        OfType.Observation -> ResourceType.Observation
        OfType.CarePlan -> ResourceType.CarePlan
        OfType.Task -> ResourceType.Task
        OfType.Condition -> ResourceType.Condition
        OfType.Appointment -> ResourceType.Appointment
        OfType.Encounter -> ResourceType.Encounter
        OfType.List -> ResourceType.List
        OfType.Practitioner -> ResourceType.Practitioner
        OfType.RelatedPerson -> ResourceType.RelatedPerson
      }
    }
    .onEach { resourceType ->
      val lastSyncTimestamp = Date().toOffsetDateTime().formatLastSyncTimestamp()
      appDataStore.saveLastUpdatedTimestamp(resourceType, lastSyncTimestamp)
    }
}
