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

package org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.fhir.ParamSyncStatus

const val SYNC_STATUS_BROADCAST_RECEIVER_KEY = "SYNC_STATUS_BROADCAST_RECEIVER_KEY"

@Suppress("DEPRECATION")
class SyncStatusBroadcastReceiver(
  val onReceiveCallback: (paramSyncStatus: ParamSyncStatus) -> Unit,
) : BroadcastReceiver() {
  override fun onReceive(context: Context?, intent: Intent?) {
    if (intent == null) return
    val paramSyncStatus =
      intent.getSerializableExtra(SYNC_STATUS_BROADCAST_RECEIVER_KEY) as? ParamSyncStatus ?: return
    onReceiveCallback(paramSyncStatus)
  }
}
