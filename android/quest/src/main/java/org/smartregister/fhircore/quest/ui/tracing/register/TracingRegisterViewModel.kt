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

package org.smartregister.fhircore.quest.ui.tracing.register

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.google.android.fhir.sync.SyncJobStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import org.smartregister.fhircore.engine.appfeature.AppFeature
import org.smartregister.fhircore.engine.appfeature.AppFeatureManager
import org.smartregister.fhircore.engine.appfeature.model.HealthModule
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.data.local.TracingRegisterFilter
import org.smartregister.fhircore.engine.data.local.register.AppRegisterRepository
import org.smartregister.fhircore.engine.domain.util.PaginationConstant
import org.smartregister.fhircore.engine.sync.OnSyncListener
import org.smartregister.fhircore.engine.sync.SyncBroadcaster
import org.smartregister.fhircore.engine.ui.filter.FilterOption
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import org.smartregister.fhircore.quest.R
import org.smartregister.fhircore.quest.data.patient.model.PatientPagingSourceState
import org.smartregister.fhircore.quest.data.register.RegisterPagingSource
import org.smartregister.fhircore.quest.navigation.MainNavigationScreen
import org.smartregister.fhircore.quest.navigation.NavigationArg
import org.smartregister.fhircore.quest.ui.StandardRegisterEvent
import org.smartregister.fhircore.quest.ui.StandardRegisterViewModel
import org.smartregister.fhircore.quest.ui.shared.models.RegisterViewData
import org.smartregister.fhircore.quest.util.REGISTER_FORM_ID_KEY
import org.smartregister.fhircore.quest.util.mappers.RegisterViewDataMapper
import org.smartregister.fhircore.quest.util.mappers.transformPatientCategoryToHealthStatus
import org.smartregister.fhircore.quest.util.mappers.transformToTracingAgeFilterEnum
import org.smartregister.fhircore.quest.util.mappers.transformTracingUiReasonToCode

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class TracingRegisterViewModel
@Inject
constructor(
  savedStateHandle: SavedStateHandle,
  syncBroadcaster: SyncBroadcaster,
  val registerRepository: AppRegisterRepository,
  val configurationRegistry: ConfigurationRegistry,
  val registerViewDataMapper: RegisterViewDataMapper,
  val dispatcherProvider: DispatcherProvider,
  val appFeatureManager: AppFeatureManager,
  val sharedPreferencesHelper: SharedPreferencesHelper,
) : ViewModel(), StandardRegisterViewModel {

  private val appFeatureName = savedStateHandle.get<String>(NavigationArg.FEATURE)
  private val healthModule =
    savedStateHandle.get<HealthModule>(NavigationArg.HEALTH_MODULE) ?: HealthModule.HOME_TRACING

  private val _isRefreshing = MutableStateFlow(false)

  override val isRefreshing: StateFlow<Boolean>
    get() = _isRefreshing.asStateFlow()

  private val _currentPage = MutableStateFlow(0)
  override val currentPage: StateFlow<Int>
    get() = _currentPage

  private val _searchText = MutableStateFlow("")
  override val searchText: StateFlow<String>
    get() = _searchText.asStateFlow()

  override val searchedText: StateFlow<String> =
    searchText
      .debounce {
        when (it.length) {
          0 -> 2.milliseconds // when search is cleared
          1,
          2, -> 1200.milliseconds
          else -> 500.milliseconds
        }
      }
      .stateIn(viewModelScope, SharingStarted.Lazily, searchText.value)

  private val _refreshCounter = MutableStateFlow(0)
  val refreshCounter: StateFlow<Int>
    get() = _refreshCounter.asStateFlow()

  private val _filtersMutableStateFlow: MutableStateFlow<TracingRegisterFilterState> =
    MutableStateFlow(TracingRegisterFilterState.default(healthModule))
  val filtersStateFlow: StateFlow<TracingRegisterFilterState> =
    _filtersMutableStateFlow.asStateFlow()

  val registerFilterFlow =
    filtersStateFlow.map(TracingRegisterFilterState::toTracingRegisterFilter).onEach { resetPage() }

  private val _paginatedRegisterData =
    combine(searchedText, _currentPage, registerFilterFlow, refreshCounter) { s, p, f, _ ->
        Triple(s, p, f)
      }
      .mapLatest {
        val pagingFlow =
          if (it.first.isNotBlank()) {
            filterRegisterDataFlow(
              text = it.first,
              filters = TracingRegisterFilterState.default(healthModule).toTracingRegisterFilter(),
            )
          } else {
            paginateRegisterDataFlow(page = it.second, filters = it.third)
          }

        return@mapLatest pagingFlow.cachedIn(viewModelScope).also { _isRefreshing.emit(false) }
      }

  override val pageRegisterListItemData:
    StateFlow<Flow<PagingData<RegisterViewData.ListItemView>>> =
    _paginatedRegisterData
      .map { pagingDataFlow ->
        pagingDataFlow.map { pagingData ->
          pagingData
            .filter { it is RegisterViewData.ListItemView }
            .map { it as RegisterViewData.ListItemView }
        }
      }
      .stateIn(
        viewModelScope.plus(dispatcherProvider.io()),
        SharingStarted.Lazily,
        initialValue = emptyFlow(),
      )

  override val pageNavigationItemViewData:
    StateFlow<Flow<PagingData<RegisterViewData.PageNavigationItemView>>> =
    _paginatedRegisterData
      .map { pagingDataFlow ->
        pagingDataFlow.map { pagingData ->
          pagingData
            .filter { it is RegisterViewData.PageNavigationItemView }
            .map { it as RegisterViewData.PageNavigationItemView }
        }
      }
      .stateIn(
        viewModelScope.plus(dispatcherProvider.io()),
        SharingStarted.Lazily,
        initialValue = emptyFlow(),
      )

  init {
    val syncStateListener = OnSyncListener { state ->
      val isStateCompleted = state is SyncJobStatus.Failed || state is SyncJobStatus.Succeeded
      if (isStateCompleted) {
        refresh()
      }
    }
    syncBroadcaster.registerSyncListener(syncStateListener, viewModelScope)
  }

  private fun resetPage() {
    _currentPage.value = 0
  }

  override fun refresh() {
    _isRefreshing.value = true
    _refreshCounter.value += 1
  }

  fun isAppFeatureHousehold() =
    appFeatureName.equals(AppFeature.HouseholdManagement.name, ignoreCase = true)

  fun paginateRegisterDataFlow(filters: TracingRegisterFilter, page: Int) =
    getPager(appFeatureName, loadAll = false, page = page, registerFilters = filters).flow

  fun filterRegisterDataFlow(text: String, filters: TracingRegisterFilter) =
    getPager(
        appFeatureName = appFeatureName,
        loadAll = true,
        searchFilter = text,
        registerFilters = filters,
        searchLoadRegister = true,
      )
      .flow

  private fun getPager(
    appFeatureName: String?,
    loadAll: Boolean = false,
    page: Int = 0,
    registerFilters: TracingRegisterFilter,
    searchFilter: String? = null,
    searchLoadRegister: Boolean = false,
  ): Pager<Int, RegisterViewData> =
    Pager(
      config =
        PagingConfig(
          pageSize = PaginationConstant.DEFAULT_PAGE_SIZE,
          initialLoadSize = PaginationConstant.DEFAULT_INITIAL_LOAD_SIZE,
          enablePlaceholders = false,
        ),
      pagingSourceFactory = {
        RegisterPagingSource(registerRepository, registerViewDataMapper, dispatcherProvider).apply {
          setPatientPagingSourceState(
            PatientPagingSourceState(
              appFeatureName = appFeatureName,
              healthModule = healthModule,
              loadAll = loadAll,
              currentPage = if (loadAll) 0 else page,
              filters = registerFilters,
              searchFilter = searchFilter,
              searchLoadRegister = searchLoadRegister,
            ),
          )
        }
      },
    )

  override fun onEvent(event: StandardRegisterEvent) {
    when (event) {
      // Search using name or patient logicalId or identifier. Modify to add more search params
      is StandardRegisterEvent.SearchRegister -> {
        _searchText.value = event.searchText
      }
      is StandardRegisterEvent.MoveToNextPage -> {
        this._currentPage.update { it + 1 }
      }
      is StandardRegisterEvent.MoveToPreviousPage -> {
        this._currentPage.update { if (it > 0) it - 1 else it }
      }
      is StandardRegisterEvent.OpenProfile -> {
        val urlParams =
          NavigationArg.bindArgumentsOf(
            Pair(NavigationArg.FEATURE, AppFeature.PatientManagement.name),
            Pair(NavigationArg.HEALTH_MODULE, healthModule),
            Pair(NavigationArg.PATIENT_ID, event.patientId),
          )
        if (healthModule == HealthModule.FAMILY) {
          event.navController.navigate(route = MainNavigationScreen.FamilyProfile.route + urlParams)
        } else
          event.navController.navigate(
            route = MainNavigationScreen.TracingProfile.route + urlParams,
          )
      }
      is StandardRegisterEvent.ApplyFilter<*> -> {
        val newFilterState = event.filterState as TracingRegisterFilterState
        _filtersMutableStateFlow.update { newFilterState }
      }
    }
  }

  fun isRegisterFormViaSettingExists(): Boolean {
    return appFeatureManager.appFeatureHasSetting(REGISTER_FORM_ID_KEY)
  }

  fun isFirstTimeSync() =
    sharedPreferencesHelper.read(SharedPreferenceKey.LAST_SYNC_TIMESTAMP.name, null).isNullOrBlank()

  fun getActiveFilters(state: TracingRegisterFilterState): List<FilterOption> {
    return state.toFilterList(healthModule)
  }

  fun clearFilters() {
    _filtersMutableStateFlow.update { TracingRegisterFilterState.default(healthModule) }
  }

  override fun progressMessage() =
    if (searchText.value.isEmpty()) {
      ""
    } else {
      configurationRegistry.context.resources.getString(R.string.search_progress_message)
    }
}

data class TracingRegisterFilterState(
  val patientAssignment: TracingRegisterUiFilter<TracingPatientAssignment>,
  val patientCategory: TracingRegisterUiFilter<TracingPatientCategory>,
  val reason: TracingRegisterUiFilter<TracingReason>,
  val age: TracingRegisterUiFilter<AgeFilter>,
) {
  fun toFilterList(healthModule: HealthModule): List<FilterOption> {
    val activeFilters: MutableList<FilterOption> = mutableListOf()
    val defaultState = default(healthModule)
    if (patientAssignment.selected != defaultState.patientAssignment.selected) {
      activeFilters.add(patientAssignment.selected)
    }
    if (patientCategory.selected != defaultState.patientCategory.selected) {
      activeFilters.add(patientCategory.selected)
    }
    if (reason.selected != defaultState.reason.selected) {
      activeFilters.add(reason.selected)
    }
    if (age.selected != defaultState.age.selected) {
      activeFilters.add(age.selected)
    }
    return activeFilters
  }

  fun toTracingRegisterFilter(): TracingRegisterFilter {
    val categories = transformPatientCategoryToHealthStatus(patientCategory.selected)
    val ageFilter = transformToTracingAgeFilterEnum(this)
    val reasonCode = transformTracingUiReasonToCode(reason.selected)
    return TracingRegisterFilter(
      patientCategory = categories,
      isAssignedToMe = patientAssignment.selected.assignedToMe(),
      age = ageFilter,
      reasonCode = reasonCode,
    )
  }

  companion object {
    fun default(healthModule: HealthModule) =
      when (healthModule) {
        HealthModule.PHONE_TRACING ->
          TracingRegisterFilterState(
            patientAssignment =
              TracingRegisterUiFilter(
                PhonePatientAssignment.ALL_PHONE,
                PhonePatientAssignment.values().asList(),
              ),
            patientCategory =
              TracingRegisterUiFilter(
                TracingPatientCategory.ALL_PATIENT_CATEGORIES,
                TracingPatientCategory.values().asList(),
              ),
            reason =
              TracingRegisterUiFilter(TracingReason.ALL_REASONS, TracingReason.values().asList()),
            age = TracingRegisterUiFilter(AgeFilter.ALL_AGES, AgeFilter.values().asList()),
          )
        HealthModule.HOME_TRACING ->
          TracingRegisterFilterState(
            patientAssignment =
              TracingRegisterUiFilter(
                HomePatientAssignment.ALL_HOME,
                HomePatientAssignment.values().asList(),
              ),
            patientCategory =
              TracingRegisterUiFilter(
                TracingPatientCategory.ALL_PATIENT_CATEGORIES,
                TracingPatientCategory.values().asList(),
              ),
            reason =
              TracingRegisterUiFilter(TracingReason.ALL_REASONS, TracingReason.values().asList()),
            age = TracingRegisterUiFilter(AgeFilter.ALL_AGES, AgeFilter.values().asList()),
          )
        else -> throw IllegalArgumentException("Not allowed")
      }
  }
}
