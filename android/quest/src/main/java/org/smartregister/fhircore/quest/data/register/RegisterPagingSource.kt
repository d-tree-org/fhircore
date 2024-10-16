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

package org.smartregister.fhircore.quest.data.register

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.withContext
import org.smartregister.fhircore.engine.domain.repository.RegisterRepository
import org.smartregister.fhircore.engine.domain.util.PaginationConstant
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.quest.data.patient.model.PatientPagingSourceState
import org.smartregister.fhircore.quest.ui.shared.models.RegisterViewData
import org.smartregister.fhircore.quest.util.mappers.RegisterViewDataMapper

/**
 * @property _patientPagingSourceState as state containing the properties used in the
 *   [RegisterRepository] function for loading data to the paging source.
 */
class RegisterPagingSource(
  private val registerRepository: RegisterRepository,
  private val registerViewDataMapper: RegisterViewDataMapper,
  private val dispatcherProvider: DispatcherProvider,
) : PagingSource<Int, RegisterViewData>() {

  private var _patientPagingSourceState = PatientPagingSourceState()

  /**
   * To load data for the current page, nextKey and prevKey for [params] are both set to null to
   * prevent automatic loading of by the [PagingSource]. This is done in order to explicitly allow
   * loading of data by manually clicking navigation previous or next buttons.
   *
   * To load all data (by automatically paginating the results in the background), set loadAll to
   * true (Checking if data is not empty prevents querying for more results when there is none):
   *
   * prevKey = if (pageNumber == 0) null else pageNumber - 1
   *
   * nextKey = if (data.isNotEmpty()) pageNumber + 1 else null
   */
  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RegisterViewData> {
    return try {
      val currentPage = params.key ?: _patientPagingSourceState.currentPage
      val registerData =
        withContext(dispatcherProvider.io()) {
          when {
            _patientPagingSourceState.searchFilter != null &&
              _patientPagingSourceState.searchLoadRegister &&
              _patientPagingSourceState.requiresFilter -> {
              registerRepository.loadRegisterFiltered(
                currentPage = currentPage,
                healthModule = _patientPagingSourceState.healthModule,
                filters = _patientPagingSourceState.filters!!,
                patientSearchText = _patientPagingSourceState.searchFilter!!,
              )
            }
            _patientPagingSourceState.searchFilter != null &&
              _patientPagingSourceState.searchLoadRegister -> {
              registerRepository.loadRegisterFiltered(
                currentPage = currentPage,
                healthModule = _patientPagingSourceState.healthModule,
                filters = _patientPagingSourceState.filters!!,
                patientSearchText = _patientPagingSourceState.searchFilter!!,
              )
            }
            _patientPagingSourceState.searchFilter != null -> {
              registerRepository.searchByName(
                currentPage = currentPage,
                appFeatureName = _patientPagingSourceState.appFeatureName,
                healthModule = _patientPagingSourceState.healthModule,
                nameQuery = _patientPagingSourceState.searchFilter!!,
              )
            }
            _patientPagingSourceState.requiresFilter -> {
              registerRepository.loadRegisterFiltered(
                currentPage = currentPage,
                healthModule = _patientPagingSourceState.healthModule,
                filters = _patientPagingSourceState.filters!!,
              )
            }
            else -> {
              registerRepository.loadRegisterData(
                currentPage = currentPage,
                loadAll = _patientPagingSourceState.loadAll,
                appFeatureName = _patientPagingSourceState.appFeatureName,
                healthModule = _patientPagingSourceState.healthModule,
              )
            }
          }
        }

      var registerViewData: MutableList<RegisterViewData> =
        registerData.map { registerViewDataMapper.transformInputToOutputModel(it) }.toMutableList()
      val prevKey: Int?
      val nextKey: Int?
      when {
        _patientPagingSourceState.loadAll -> {
          prevKey = if (currentPage == 0) null else currentPage - 1
          nextKey = if (registerViewData.isNotEmpty()) currentPage + 1 else null
        }
        else -> {
          prevKey = null
          nextKey = null

          val hasNext = registerViewData.size > PaginationConstant.DEFAULT_PAGE_SIZE
          if (hasNext) {
            registerViewData = registerViewData.subList(0, PaginationConstant.DEFAULT_PAGE_SIZE)
          }
          registerViewData.add(
            RegisterViewData.PageNavigationItemView(
              currentPage + 1,
              hasNext,
              hasPrev = currentPage > 0,
            ),
          )
        }
      }

      LoadResult.Page(data = registerViewData, prevKey = prevKey, nextKey = nextKey)
    } catch (exception: Exception) {
      LoadResult.Error(exception)
    }
  }

  fun setPatientPagingSourceState(patientPagingSourceState: PatientPagingSourceState) {
    this._patientPagingSourceState = patientPagingSourceState
  }

  override fun getRefreshKey(state: PagingState<Int, RegisterViewData>): Int? {
    return state.anchorPosition
  }
}
