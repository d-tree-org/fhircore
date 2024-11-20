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

package org.smartregister.fhircore.quest.ui.patient.register

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.google.android.fhir.sync.SyncJobStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.smartregister.fhircore.engine.appfeature.AppFeature
import org.smartregister.fhircore.engine.appfeature.AppFeatureManager
import org.smartregister.fhircore.engine.appfeature.model.HealthModule
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.data.local.register.AppRegisterRepository
import org.smartregister.fhircore.engine.data.local.register.dao.HivRegisterDao
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirApiService
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceService
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.ApiRepositoryImpl
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.EventCallback
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.ListResourceExt
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.Progress
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SearchBy
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SyncState
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SyncState.CompletedInitialSync
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SyncState.InitialSync
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SyncState.SubSequentRunSync
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SyncState.SubSequentSync
import org.smartregister.fhircore.engine.domain.util.PaginationConstant
import org.smartregister.fhircore.engine.sync.SyncBroadcaster
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireActivity
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireType
import org.smartregister.fhircore.engine.util.AppDataStore
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.SharedPreferenceKey.LAST_SYNC_TIMESTAMP
import org.smartregister.fhircore.engine.util.SharedPreferenceKey.SYNC_STATUS
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import org.smartregister.fhircore.quest.R
import org.smartregister.fhircore.quest.data.patient.model.PatientPagingSourceState
import org.smartregister.fhircore.quest.data.register.RegisterPagingSource
import org.smartregister.fhircore.quest.navigation.MainNavigationScreen
import org.smartregister.fhircore.quest.navigation.NavigationArg
import org.smartregister.fhircore.quest.ui.shared.models.RegisterViewData
import org.smartregister.fhircore.quest.util.REGISTER_FORM_ID_KEY
import org.smartregister.fhircore.quest.util.mappers.RegisterViewDataMapper
import timber.log.Timber

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PatientRegisterViewModel
@Inject
constructor(
  savedStateHandle: SavedStateHandle,
  val registerRepository: AppRegisterRepository,
  val syncBroadcaster: SyncBroadcaster,
  val configurationRegistry: ConfigurationRegistry,
  val registerViewDataMapper: RegisterViewDataMapper,
  val appFeatureManager: AppFeatureManager,
  val dispatcherProvider: DispatcherProvider,
  val sharedPreferencesHelper: SharedPreferencesHelper,
  fhirResourceService: FhirResourceService,
  fhirApiService: FhirApiService,
  val hivRegisterDao: HivRegisterDao,
  appDataStore: AppDataStore,
) : ViewModel() {

  private val appFeatureName = savedStateHandle.get<String>(NavigationArg.FEATURE)
  private val healthModule =
    savedStateHandle.get<HealthModule>(NavigationArg.HEALTH_MODULE) ?: HealthModule.DEFAULT

  private val _isRefreshing = MutableStateFlow(false)

  private val apiRepository =
    ApiRepositoryImpl(
      fhirApiService = fhirApiService,
      fhirEngine = syncBroadcaster.fhirEngine,
      sharedPreferencesHelper = sharedPreferencesHelper,
      appDataStore = appDataStore,
      fhirResourceService = fhirResourceService,
    )

  private val _progressStatusUiState = MutableStateFlow(PatientUiState())
  val progressStatusUiState = _progressStatusUiState.asStateFlow()
  private val _channel = Channel<EventCallback>()
  val channelFlow = _channel.receiveAsFlow()
  private var invokeCounter = 0

  val isRefreshing: StateFlow<Boolean>
    get() = _isRefreshing.asStateFlow()

  private val _currentPage = MutableStateFlow(0)
  val currentPage
    get() = _currentPage.asStateFlow()

  private val _searchText = MutableStateFlow("")
  val searchText: StateFlow<String>
    get() = _searchText.asStateFlow()

  val searchedText: StateFlow<String> =
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

  private val _firstTimeSyncState = MutableStateFlow(isFirstTimeSync())
  val firstTimeSyncState: StateFlow<Boolean>
    get() = _firstTimeSyncState.asStateFlow()

  private val _syncProgressMutableStateFlow = MutableStateFlow("")
  val syncProgressStateFlow = _syncProgressMutableStateFlow.asStateFlow()

  private val _paginatedRegisterViewData: Flow<Flow<PagingData<RegisterViewData>>> =
    combine(
        searchedText,
        currentPage,
        refreshCounter,
      ) { s, p, r ->
        Triple(s, p, r)
      }
      .mapLatest {
        val pagingFlow =
          when {
            it.first.isNotBlank() -> {
              filterRegisterDataFlow(text = it.first)
            }
            else -> {
              paginateRegisterDataFlow(page = it.second)
            }
          }

        return@mapLatest pagingFlow.cachedIn(viewModelScope).also { _isRefreshing.emit(false) }
      }

  var pageRegisterListItemData =
    _paginatedRegisterViewData
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

  val pageNavigationItemViewData =
    _paginatedRegisterViewData
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

  data class PatientUiState(val progress: Progress? = null)

  fun initialSync() {
    sharedPreferencesHelper.write(SYNC_STATUS.name, InitialSync.value)
    syncBroadcaster.runSync()
  }

  fun getSyncState() = sharedPreferencesHelper.read(SYNC_STATUS.name, SyncState.ShowDialog.value)

  private suspend fun onSetupState() {
    if (getSyncState() == SyncState.ShowDialog.value) {
      _channel.send(EventCallback.ShowAttentionDialog)
    }

    if (getSyncState() == InitialSync.value) {
      sharedPreferencesHelper.write(
        SYNC_STATUS.name,
        CompletedInitialSync.value,
      )
    }

    if (getSyncState() == CompletedInitialSync.value || getSyncState() == SubSequentRunSync.value) {
      apiRepository.invoke(
        onCompleteListener = { patientSize ->
          patientSize
            .takeIf { it > 0 }
            ?.let {
              viewModelScope.launch {
                _channel.send(EventCallback.OnSyncListener(true))
                syncBroadcaster.runSync()
              }
            }
        },
        onProgress = { progressUiState ->
          viewModelScope.launch { _channel.send(EventCallback.InProgress) }
          _progressStatusUiState.update { it.copy(progress = progressUiState) }
        },
      )
    }

    if (getSyncState() == SubSequentSync.value) {
      ListResourceExt(
          fhirEngine = syncBroadcaster.fhirEngine,
          sharedPreferences = sharedPreferencesHelper,
          apiRepository = apiRepository,
        )
        .onListResourceExt { progressUiState ->
          if (progressUiState.totalResources > 0) {
            viewModelScope.launch {
              _channel.send(EventCallback.InProgress)
              _progressStatusUiState.update { it.copy(progress = progressUiState) }

              if (progressUiState.currentPosition.plus(1) == progressUiState.totalResources) {
                sharedPreferencesHelper.write(SYNC_STATUS.name, SubSequentRunSync.value)
                _channel.send(EventCallback.OnSyncListener(true))
                syncBroadcaster.runSync()
              }
            }
          }
        }
    }
  }

  init {
    syncBroadcaster.registerSyncListener(
      { state ->
        when (state) {
          is SyncJobStatus.Failed -> {
            refresh()
            _firstTimeSyncState.value = false
          }
          is SyncJobStatus.Succeeded -> {
            refresh()
            _firstTimeSyncState.value = false
            viewModelScope.launch {
              invokeCounter += 1
              if (invokeCounter == 1) {
                onSetupState()
              }

              Timber.e("invokeCounter -> $invokeCounter")
            }
          }
          is SyncJobStatus.Started -> {
            invokeCounter = 0
          }
          is SyncJobStatus.InProgress -> {
            updateSyncProgress(state)
            _firstTimeSyncState.value = isFirstTimeSync()
          }
          else -> _firstTimeSyncState.value = isFirstTimeSync()
        }
      },
      scope = viewModelScope,
    )
  }

  private fun updateSyncProgress(state: SyncJobStatus.InProgress) {
    val percentage = state.completed.div(max(state.total, 1).toDouble()).times(100).toInt()
    if (percentage > 0) {
      _syncProgressMutableStateFlow.update {
        "$percentage% ${state.syncOperation.name.lowercase()}ed"
      }
    }
  }

  fun refresh() {
    _isRefreshing.update { true }
    _refreshCounter.update { it + 1 }
  }

  fun isAppFeatureHousehold() =
    appFeatureName.equals(AppFeature.HouseholdManagement.name, ignoreCase = true)

  private fun paginateRegisterDataFlow(page: Int) =
    getPager(appFeatureName, healthModule, loadAll = false, page = page).flow

  private fun filterRegisterDataFlow(text: String) =
    getPager(
        appFeatureName = appFeatureName,
        healthModule = healthModule,
        loadAll = true,
        searchFilter = text,
      )
      .flow

  private var searchJob: Job? = null

  fun searchPatient(searchQuery: String) {
    //    searchJob?.cancel()
    viewModelScope.launch {
      //      delay(2000)
      val criteria =
        when {
          searchQuery.any { it.isDigit() } -> SearchBy.IDENTIFIER
          else -> SearchBy.HUMAN_NAME
        }

      pageRegisterListItemData =
        patientIdentifierPager(searchQuery, criteria)
          .cachedIn(viewModelScope)
          .map { flowOf(it) }
          .stateIn(
            viewModelScope.plus(dispatcherProvider.io()),
            SharingStarted.Lazily,
            initialValue = emptyFlow(),
          )
    }
  }

  fun showLocalData() {
    pageRegisterListItemData =
      _paginatedRegisterViewData
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
  }

  fun fetchAndSaveToDb(logicalId: String) =
    viewModelScope.launch {
      _channel.send(EventCallback.Stated)
      apiRepository.fetchAndSaveToDb(
        logicalId,
        onCompleteListener = {
          launch {
            _channel.send(EventCallback.Finished(logicalId))
            syncBroadcaster.runSync()
          }
        },
      )
    }

  inner class PatientIdentifierPagingSource(
    private val searchQuery: String,
    private val criteria: SearchBy,
  ) : PagingSource<Int, RegisterViewData.ListItemView>() {
    override suspend fun load(
      params: LoadParams<Int>,
    ): LoadResult<Int, RegisterViewData.ListItemView> {
      return try {
        val registerViewData =
          apiRepository
            .search(searchQuery, criteria)
            .map { hivRegisterDao.transformPatientToHivRegisterData(it) }
            .map { registerViewDataMapper.transformInputToOutputModel(it).copy(isLocal = false) }
        LoadResult.Page(
          data = registerViewData,
          prevKey = null,
          nextKey = null,
        )
      } catch (exception: Exception) {
        LoadResult.Error(exception)
      }
    }

    override fun getRefreshKey(state: PagingState<Int, RegisterViewData.ListItemView>) = null
  }

  private fun patientIdentifierPager(
    searchQuery: String,
    criteria: SearchBy,
  ): Flow<PagingData<RegisterViewData.ListItemView>> =
    Pager(
        config =
          PagingConfig(
            pageSize = PaginationConstant.DEFAULT_PAGE_SIZE,
            initialLoadSize = PaginationConstant.DEFAULT_INITIAL_LOAD_SIZE,
            enablePlaceholders = false,
          ),
        pagingSourceFactory = { PatientIdentifierPagingSource(searchQuery, criteria) },
      )
      .flow

  private fun getPager(
    appFeatureName: String?,
    healthModule: HealthModule,
    loadAll: Boolean = false,
    page: Int = 0,
    searchFilter: String? = null,
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
              searchFilter = searchFilter,
            ),
          )
        }
      },
    )

  fun patientRegisterQuestionnaireIntent(context: Context) =
    Intent(context, QuestionnaireActivity::class.java)
      .putExtras(
        QuestionnaireActivity.intentArgs(
          formName = configurationRegistry.getAppConfigs().registrationForm,
          questionnaireType = QuestionnaireType.DEFAULT,
        ),
      )

  fun onEvent(event: PatientRegisterEvent) {
    when (event) {
      // Search using name or patient logicalId or identifier. Modify to add more search params
      is PatientRegisterEvent.SearchRegister -> {
        _searchText.value = event.searchText
      }
      is PatientRegisterEvent.MoveToNextPage -> {
        this._currentPage.update { it + 1 }
      }
      is PatientRegisterEvent.MoveToPreviousPage -> {
        this._currentPage.update { if (it > 0) it - 1 else it }
      }
      is PatientRegisterEvent.RegisterNewClient -> {
        //        event.context.launchQuestionnaire<QuestionnaireActivity>(
        //          registerViewConfiguration.registrationForm
        //        )
      }
      is PatientRegisterEvent.OpenProfile -> {
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
            route = MainNavigationScreen.PatientProfile.route + urlParams,
          )
      }
    }
  }

  fun isRegisterFormViaSettingExists(): Boolean {
    return appFeatureManager.appFeatureHasSetting(REGISTER_FORM_ID_KEY)
  }

  fun isFirstTimeSync() =
    sharedPreferencesHelper.read(LAST_SYNC_TIMESTAMP.name, null).isNullOrBlank()

  fun progressMessage() =
    if (searchedText.value.isEmpty()) {
      ""
    } else {
      configurationRegistry.context.resources.getString(R.string.search_progress_message)
    }
}
