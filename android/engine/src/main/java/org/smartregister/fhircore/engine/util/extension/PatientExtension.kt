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

import android.content.Context
import androidx.core.text.isDigitsOnly
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.Operation
import com.google.android.fhir.search.Search
import com.google.android.fhir.search.search
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.hl7.fhir.r4.model.Address
import org.hl7.fhir.r4.model.CarePlan
import org.hl7.fhir.r4.model.CodeType
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.HumanName
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Immunization
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.codesystems.AdministrativeGender
import org.smartregister.fhircore.engine.R
import org.smartregister.fhircore.engine.data.domain.PhoneContact
import org.smartregister.fhircore.engine.data.domain.PregnancyStatus
import org.smartregister.fhircore.engine.data.local.DefaultRepository
import org.smartregister.fhircore.engine.domain.model.HealthStatus
import org.smartregister.fhircore.engine.util.SystemConstants
import timber.log.Timber

private const val RISK = "risk"
const val DAYS_IN_YEAR = 365
const val DAYS_IN_MONTH = 30
const val DAYS_IN_WEEK = 7

fun List<HumanName>.canonicalName(): String {
  val humanName = this.firstOrNull()
  return if (humanName != null) {
    (humanName.given + humanName.family).filterNotNull().joinToString(" ") {
      it.toString().trim().capitalizeFirstLetter()
    }
  } else {
    ""
  }
}

fun Patient.extractName(): String {
  if (!hasName()) return ""
  return this.name.canonicalName()
}

fun List<HumanName>.familyName(): String {
  val humanName = this.firstOrNull()
  return if (humanName != null) {
    humanName.family?.capitalizeFirstLetter()?.plus(" Family") ?: ""
  } else {
    ""
  }
}

fun Patient.extractFamilyName(): String {
  if (!hasName()) return ""
  return this.name.familyName()
}

fun List<HumanName>.givenName(): String {
  val humanName = this.firstOrNull()
  return if (humanName != null) {
    humanName.given?.toString()?.trim('[')?.trim(']')?.capitalizeFirstLetter() ?: ""
  } else {
    ""
  }
}

fun Patient.extractGivenName(): String {
  if (!hasName()) return ""
  return this.name.givenName()
}

fun String.capitalizeFirstLetter() = replaceFirstChar { it.titlecase(Locale.getDefault()) }

fun Patient.extractGender(context: Context): String? =
  if (hasGender()) {
    when (AdministrativeGender.valueOf(this.gender.name)) {
      AdministrativeGender.MALE -> context.getString(R.string.male)
      AdministrativeGender.FEMALE -> context.getString(R.string.female)
      AdministrativeGender.OTHER -> context.getString(R.string.other)
      AdministrativeGender.UNKNOWN -> context.getString(R.string.unknown)
      AdministrativeGender.NULL -> ""
    }
  } else {
    null
  }

fun Patient.extractAge(): String {
  if (!hasBirthDate()) return ""
  return getAgeStringFromDays(birthDate.daysPassed())
}

fun getAgeStringFromDays(days: Long): String {
  var elapseYearsString = ""
  var elapseMonthsString = ""
  var elapseWeeksString = ""
  var elapseDaysString = ""
  val elapsedYears = days / DAYS_IN_YEAR
  val diffDaysFromYear = days % DAYS_IN_YEAR
  val elapsedMonths = diffDaysFromYear / DAYS_IN_MONTH
  val diffDaysFromMonth = diffDaysFromYear % DAYS_IN_MONTH
  val elapsedWeeks = diffDaysFromMonth / DAYS_IN_WEEK
  val elapsedDays = diffDaysFromMonth % DAYS_IN_WEEK
  // TODO use translatable abbreviations - extract abbr to string resource
  if (elapsedYears > 0) elapseYearsString = elapsedYears.toString() + "y"
  if (elapsedMonths > 0) elapseMonthsString = elapsedMonths.toString() + "m"
  if (elapsedWeeks > 0) elapseWeeksString = elapsedWeeks.toString() + "w"
  if (elapsedDays >= 0) elapseDaysString = elapsedDays.toString() + "d"

  return if (days >= DAYS_IN_YEAR * 10) {
    elapseYearsString
  } else if (days >= DAYS_IN_YEAR) {
    if (elapsedMonths > 0) {
      "$elapseYearsString $elapseMonthsString"
    } else {
      elapseYearsString
    }
  } else if (days >= DAYS_IN_MONTH) {
    if (elapsedWeeks > 0) {
      "$elapseMonthsString $elapseWeeksString"
    } else {
      elapseMonthsString
    }
  } else if (days >= DAYS_IN_WEEK) {
    if (elapsedDays > 0) {
      "$elapseWeeksString $elapseDaysString"
    } else {
      elapseWeeksString
    }
  } else {
    elapseDaysString
  }
}

fun Patient.atRisk() =
  this.extension.singleOrNull { it.value.toString().contains(RISK) }?.value?.toString() ?: ""

fun Patient.getLastSeen(immunizations: List<Immunization>): String {
  return immunizations
    .maxByOrNull { it.protocolAppliedFirstRep.doseNumberPositiveIntType.value }
    ?.occurrenceDateTimeType
    ?.toDisplay() ?: this.meta?.lastUpdated.lastSeenFormat()
}

fun Date?.lastSeenFormat(): String {
  return if (this != null) {
    SimpleDateFormat("MM-dd-yyyy", Locale.ENGLISH).run { format(this@lastSeenFormat) }
  } else {
    ""
  }
}

fun Address.canonical(): String {
  return with(this) {
    val addressLine =
      if (this.hasLine()) this.line.joinToString(separator = ", ", postfix = ", ") else ""

    addressLine
      .join(this.district, " ")
      .join(this.city, " ")
      .join(this.state, " ")
      .join(this.country, " ")
      .trim()
  }
}

fun Patient.extractAddress(): String {
  if (!hasAddress()) return ""
  return addressFirstRep.canonical()
}

fun Patient.extractAddressDistrict(): String {
  return with(addressFirstRep) { this.district ?: "" }
}

fun Patient.extractAddressState(): String {
  return with(addressFirstRep) { this.state ?: "" }
}

fun Patient.extractAddressText(): String {
  return with(addressFirstRep) { this.text ?: "" }
}

fun Patient.extractTelecom(): List<PhoneContact> {
  if (!hasTelecom()) return emptyList()
  return telecom.mapNotNull {
    val raw = it.value.split("|")
    if (raw.size > 1) {
      PhoneContact(raw.getOrNull(1) ?: "", raw.getOrNull(2) ?: "")
    } else if (raw.size == 1) {
      if (it.value.isDigitsOnly()) {
        PhoneContact(it.value, "Self")
      } else {
        null
      }
    } else {
      null
    }
  }
}

fun Patient.extractGeneralPractitionerReference(): String {
  return generalPractitioner.firstOrNull { !it.isEmpty }?.reference ?: ""
}

fun Patient.extractManagingOrganizationReference(): String {
  if (!hasManagingOrganization()) return ""
  return with(managingOrganization) { this.reference }
}

fun Patient.extractDeathDate() =
  if (this.hasDeceasedDateTimeType()) deceasedDateTimeType?.value else null

fun String?.join(other: String?, separator: String) =
  this.orEmpty().plus(other?.plus(separator).orEmpty())

fun Patient.extractFamilyTag() =
  this.meta.tag.firstOrNull {
    it.display.contentEquals("family", true) || it.display.contains("head", true)
  }

fun Patient.isFamilyHead() = this.extractFamilyTag() != null

fun List<Condition>.hasActivePregnancy() =
  this.any { condition ->
    // is active and any of the display / text in code is pregnant
    val active = condition.clinicalStatus.coding.any { it.code == "active" }
    val pregnancy =
      condition.code.coding
        .map { it.display }
        .plus(condition.code.text)
        .any { it.contentEquals("pregnant", true) }

    active && pregnancy
  }

fun List<Condition>.activelyBreastfeeding() =
  this.any { condition ->
    val active = condition.clinicalStatus.coding.any { it.code == "active" }
    val pregnancy =
      condition.code.coding
        .map { it.display }
        .plus(condition.code.text)
        .any { it.contentEquals("breastfeeding", true) }

    active && pregnancy
  }

fun List<Condition>.pregnancyCondition(): Condition {
  var pregnancyCondition = Condition()
  this.forEach { condition ->
    if (
      condition.code.coding
        .map { it.display }
        .plus(condition.code.text)
        .any { it.contentEquals("pregnant", true) }
    ) {
      pregnancyCondition = condition
    }
  }

  return pregnancyCondition
}

suspend fun DefaultRepository.getPregnancyStatus(patientId: String): PregnancyStatus {
  val conditions =
    activePatientConditions(patientId) {
      filter(
        Condition.CODE,
        { value = of(CodeType("77386006")) },
        { value = of(CodeType("413712001")) },
        operation = Operation.OR,
      )
    }

  if (conditions.isEmpty()) return PregnancyStatus.None
  val isPregnant = conditions.findLast { it.code.codingFirstRep.code == "77386006" } != null
  if (isPregnant) return PregnancyStatus.Pregnant
  return PregnancyStatus.BreastFeeding
}

fun List<Condition>.getPregnancyStatus(): PregnancyStatus {
  if (isEmpty()) return PregnancyStatus.None
  val isPregnant = findLast { it.code.codingFirstRep.code == "77386006" } != null
  return if (isPregnant) PregnancyStatus.Pregnant else PregnancyStatus.BreastFeeding
}

fun Enumerations.AdministrativeGender.translateGender(context: Context) =
  when (this) {
    Enumerations.AdministrativeGender.MALE -> context.getString(R.string.male)
    Enumerations.AdministrativeGender.FEMALE -> context.getString(R.string.female)
    else -> context.getString(R.string.unknown)
  }

fun Patient.extractSecondaryIdentifier(): String? {
  if (this.hasIdentifier()) {
    return this.identifier.lastOrNull { it.use == Identifier.IdentifierUse.SECONDARY }?.value
  }
  return null
}

fun Patient.extractPatientTypeCoding(): Coding? {
  val patientTypes =
    this.meta.tag.filter {
      it.system == SystemConstants.PATIENT_TYPE_FILTER_TAG_VIA_META_CODINGS_SYSTEM
    }
  val patientType: String? = SystemConstants.getCodeByPriority(patientTypes.map { it.code })
  return patientTypes.firstOrNull { patientType == it.code }
}

fun Patient.extractOfficialIdentifier(): String? {
  val patientTypes =
    this.meta.tag
      .filter { it.system == SystemConstants.PATIENT_TYPE_FILTER_TAG_VIA_META_CODINGS_SYSTEM }
      .map { it.code }
  val patientType: String? = SystemConstants.getCodeByPriority(patientTypes)
  return if (this.hasIdentifier() && patientType != null) {
    var actualId: Identifier? = null
    var hasNewSystem = false
    for (pId in this.identifier) {
      if (pId.system?.contains("https://d-tree.org/fhir/patient-identifier") == true) {
        hasNewSystem = true
      }
      if (pId.system == SystemConstants.getIdentifierSystemFromPatientType(patientType)) {
        actualId = pId
      }
    }
    if (!hasNewSystem) {
      this.identifier
        .lastOrNull { it.use == Identifier.IdentifierUse.OFFICIAL && it.system != "WHO-HCID" }
        ?.value
    } else {
      actualId?.value
    }
  } else {
    null
  }
}

fun Coding.toHealthStatus(): HealthStatus {
  return try {
    HealthStatus.valueOf(this.code.uppercase(Locale.getDefault()).replace("-", "_")).apply {
      this@apply.display =
        when (this@apply) {
          HealthStatus.NEWLY_DIAGNOSED_CLIENT,
          HealthStatus.CLIENT_ALREADY_ON_ART, -> "ART Client"
          HealthStatus.COMMUNITY_POSITIVE -> "No Conf Test"
          else -> this@toHealthStatus.display
        }
    }
  } catch (e: Exception) {
    Timber.e(e)
    HealthStatus.DEFAULT
  }
}

fun Patient.extractHealthStatusFromMeta(filterTag: String): HealthStatus {
  return this.extractPatientTypeCoding()?.toHealthStatus() ?: HealthStatus.DEFAULT
}

suspend fun Patient.activeCarePlans(fhirEngine: FhirEngine): List<CarePlan> {
  return fhirEngine
    .search<CarePlan> {
      filter(CarePlan.SUBJECT, { value = this@activeCarePlans.referenceValue() })
      filter(CarePlan.STATUS, { value = of(CarePlan.CarePlanStatus.ACTIVE.toCode()) })
    }
    .asSequence()
    .map { it.resource }
    .filter { it.status == CarePlan.CarePlanStatus.ACTIVE }
    .sortedByDescending { it.period.start }
    .toList()
}

suspend fun DefaultRepository.activePatientConditions(
  patientId: String,
  filters: (Search.() -> Unit)? = null,
): List<Condition> {
  return fhirEngine
    .search<Condition> {
      filter(Condition.SUBJECT, { value = "${ResourceType.Patient.name}/$patientId" })
      filters?.invoke(this)
      filter(
        Condition.VERIFICATION_STATUS,
        {
          value =
            of(
              CodeableConcept()
                .addCoding(
                  Coding(
                    "https://terminology.hl7.org/CodeSystem/condition-ver-status",
                    "confirmed",
                    null,
                  ),
                ),
            )
        },
      )
      filter(
        Condition.CLINICAL_STATUS,
        {
          value =
            of(
              CodeableConcept()
                .addCoding(
                  Coding(
                    "https://terminology.hl7.org/CodeSystem/condition-clinical",
                    "active",
                    null,
                  ),
                ),
            )
        },
      )
    }
    .map { it.resource }
}
