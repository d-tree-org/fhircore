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

package org.smartregister.fhircore.engine.p2p.dao

import androidx.annotation.NonNull
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.extensions.logicalId
import java.util.TreeSet
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.p2p.dao.ReceiverTransferDao
import org.smartregister.p2p.sync.DataType
import timber.log.Timber

open class P2PReceiverTransferDao
@Inject
constructor(fhirEngine: FhirEngine, dispatcherProvider: DispatcherProvider) :
  BaseP2PTransferDao(fhirEngine, dispatcherProvider), ReceiverTransferDao {

  override fun getP2PDataTypes(): TreeSet<DataType> = getDataTypes()

  override fun receiveJson(@NonNull type: DataType, @NonNull jsonArray: JSONArray): Long {
    var maxLastUpdated = 0L
    Timber.e("saving resources from base dai")
    (0 until jsonArray.length()).forEach {
      runBlocking {
        val resource =
          jsonParser.parseResource(resourceClassType(type), jsonArray.get(it).toString())
        addOrUpdate(resource = resource)
        maxLastUpdated =
          (if (resource.meta.lastUpdated.time > maxLastUpdated) {
            resource.meta.lastUpdated.time
          } else {
            maxLastUpdated
          })
        Timber.e("Received ${resource.resourceType} with id = ${resource.logicalId}")
      }
    }
    Timber.e("max last updated is $maxLastUpdated")
    return maxLastUpdated
  }
}
