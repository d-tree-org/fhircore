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

package org.dtree.fhircore.dataclerk.ui.home.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import org.dtree.fhircore.dataclerk.ui.main.AppDataStore
import org.dtree.fhircore.dataclerk.ui.main.PatientItem
import timber.log.Timber

class PatientPagingSource(private val dataStore: AppDataStore) : PagingSource<Int, PatientItem>() {
  override fun getRefreshKey(state: PagingState<Int, PatientItem>): Int? {
    return state.anchorPosition?.let { anchorPosition ->
      val anchorPage = state.closestPageToPosition(anchorPosition)
      anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
    }
  }

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PatientItem> {
    return try {
      val response = dataStore.loadPatients()
      val nextPage = null
      Timber.e("next page is $nextPage")
      LoadResult.Page(
        data = response,
        prevKey = null,
        nextKey = nextPage,
      )
    } catch (e: Exception) {
      Timber.e(e)
      LoadResult.Error(e)
    }
  }
}
