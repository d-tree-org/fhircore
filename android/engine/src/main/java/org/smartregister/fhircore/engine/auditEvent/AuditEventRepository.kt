package org.smartregister.fhircore.engine.auditEvent

import com.google.android.fhir.FhirEngine
import org.hl7.fhir.r4.model.AuditEvent
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Practitioner
import org.smartregister.fhircore.engine.data.local.DefaultRepository
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import org.smartregister.fhircore.engine.util.extension.asReference
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class AuditEventRepository
@Inject
constructor(
    val defaultRepository: DefaultRepository,
    val sharedPreferences: SharedPreferencesHelper,
): IAuditEventRepository {
    override suspend fun createAuditEvent() {
        // Get Practitioner Resource
        val practitionerID =
            sharedPreferences.read(key = SharedPreferenceKey.PRACTITIONER_ID.name, defaultValue = null)
        val practitioner = defaultRepository.loadResource<Practitioner>(practitionerID!!)

        // Create AuditEvent Resource
        val auditEvent = AuditEvent().apply {
            id = UUID.randomUUID().toString()
            type = Coding().apply {
                system = "http://dicom.nema.org/resources/ontology/DCM"
                code ="110114"
                display = "User Authentication"
            }
            subtype = listOf(
                Coding().apply {
                    system = "http://dicom.nema.org/resources/ontology/DCM"
                    code = "110122"
                    display = "Login"
                }
            )
            outcome = AuditEvent.AuditEventOutcome._0
            action = AuditEvent.AuditEventAction.C
            recorded = Date()
            agent = listOf(
                AuditEvent.AuditEventAgentComponent().apply {
                    who = practitioner?.asReference()
                    requestor = true
                }
            )
            source = AuditEvent.AuditEventSourceComponent().apply {
                site = "https://d-tree.org"
                observer = practitioner?.asReference()
            }
        }

        // Save AuditEvent Resource
        defaultRepository.addOrUpdate(true, auditEvent,)
    }
}



interface IAuditEventRepository {
    suspend fun createAuditEvent()
}