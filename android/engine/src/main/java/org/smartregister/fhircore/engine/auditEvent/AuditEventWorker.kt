package org.smartregister.fhircore.engine.auditEvent

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.logicalId
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import org.hl7.fhir.r4.model.AuditEvent
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Practitioner
import org.smartregister.fhircore.engine.data.local.DefaultRepository
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import org.smartregister.fhircore.engine.util.extension.asReference
import timber.log.Timber
import java.util.Date
import java.util.UUID

@HiltWorker
class AuditEventWorker
@AssistedInject
constructor(
    @Assisted val appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val auditEventRepository: AuditEventRepository
    ) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        Timber.e("AuditEventWorker is running")
        auditEventRepository.createAuditEvent()
        return Result.success()
    }

    companion object{
        const val NAME = "AuditEventWorker"
    }

}