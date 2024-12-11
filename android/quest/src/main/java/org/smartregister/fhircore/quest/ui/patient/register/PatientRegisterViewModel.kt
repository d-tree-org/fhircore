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
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.google.android.fhir.sync.SyncJobStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.Patient
import org.smartregister.fhircore.engine.appfeature.AppFeature
import org.smartregister.fhircore.engine.appfeature.AppFeatureManager
import org.smartregister.fhircore.engine.appfeature.model.HealthModule
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.data.local.TingatheDatabase
import org.smartregister.fhircore.engine.data.local.register.AppRegisterRepository
import org.smartregister.fhircore.engine.data.local.syncStrategy.toEntity
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceService
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.ApiRepositoryImpl
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.logicalIds
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.syncConfigOfflineFirst
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SearchBy
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SyncState
import org.smartregister.fhircore.engine.domain.util.PaginationConstant
import org.smartregister.fhircore.engine.sync.SyncBroadcaster
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireActivity
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireType
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import org.smartregister.fhircore.engine.util.extension.extractName
import org.smartregister.fhircore.engine.util.extension.showToast
import org.smartregister.fhircore.quest.R
import org.smartregister.fhircore.quest.data.patient.model.PatientPagingSourceState
import org.smartregister.fhircore.quest.data.register.RegisterPagingSource
import org.smartregister.fhircore.quest.navigation.MainNavigationScreen
import org.smartregister.fhircore.quest.navigation.NavigationArg
import org.smartregister.fhircore.quest.ui.shared.models.RegisterViewData
import org.smartregister.fhircore.quest.util.REGISTER_FORM_ID_KEY
import org.smartregister.fhircore.quest.util.mappers.RegisterViewDataMapper
import timber.log.Timber

data class PatientId(
  val identifier: String,
  val dateOfBirth: Date,
  val humanName: String,
  val uuid: String,
)

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
  private val database: TingatheDatabase,
  fhirResourceService: FhirResourceService,
) : ViewModel() {

  private val appFeatureName = savedStateHandle.get<String>(NavigationArg.FEATURE)
  private val healthModule =
    savedStateHandle.get<HealthModule>(NavigationArg.HEALTH_MODULE) ?: HealthModule.DEFAULT

  private val apiRepositoryImpl = ApiRepositoryImpl(fhirResourceService, sharedPreferencesHelper)
  private val _isRefreshing = MutableStateFlow(false)

  private val _identifiers = MutableStateFlow<MutableList<PatientId>>(mutableListOf())
  val identifiers: StateFlow<MutableList<PatientId>> = _identifiers

  private val _isSearching: MutableStateFlow<Boolean> = MutableStateFlow(false)
  private val _queryString: MutableStateFlow<String> = MutableStateFlow("")
  val queryString = _queryString.asStateFlow()
  val isSearching = _isSearching.asStateFlow()

  fun onAddIdentifier(patients: List<Patient>) {
    patients.forEach { patient ->
      _identifiers.value =
        _identifiers.value.apply {
          if (patient.idPart !in this.map { it.uuid }) {
            add(
              PatientId(
                uuid = patient.idPart,
                identifier = patient.identifier.firstOrNull()?.value ?: "",
                dateOfBirth = patient.birthDate,
                humanName = patient.extractName(),
              ),
            )
          }
        }
    }
  }

  fun onDeleteIdentifier(patientId: PatientId) {
    _identifiers.value = _identifiers.value.toMutableList().apply { remove(patientId) }
  }

  fun isOfflineFirst() = syncConfigOfflineFirst(configurationRegistry, sharedPreferencesHelper)

  fun onSyncNow() {
    viewModelScope.launch {
      database.syncStrategyCacheDao.insert(identifiers.value.map { it.uuid.toEntity() })
      _identifiers.value.clear()
      withContext(Dispatchers.Main) {
        syncBroadcaster.appContext.showToast("Syncing...")
        syncBroadcaster.runSync()
      }
    }
  }

  fun onValueChange(query: String) {
    _isSearching.value = query.isNotEmpty()
    _queryString.update { query }
  }

  private fun identifierLookup() {
    viewModelScope.launch {
      _queryString
        .debounce(2_000)
        .filterNot { it.trim().isEmpty() }
        .collectLatest { query ->
          val searchBy = if (query.isDigitsOnly()) SearchBy.IDENTIFIER else SearchBy.HUMAN_NAME
          kotlin
            .runCatching { apiRepositoryImpl.search(query, searchBy) }
            .onSuccess {
              _isSearching.value = false
              if (it.isEmpty()) {
                sharedPreferencesHelper.context.showToast("No patient found with ID $query")
                return@onSuccess
              }
              _queryString.update { "" }
              onAddIdentifier(it)
            }
            .onFailure {
              sharedPreferencesHelper.context.showToast("Something went wrong, please try again")
              _isSearching.value = false
              Timber.e(it)
            }
        }
    }
  }

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

  private val _paginatedRegisterViewData =
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

  val pageRegisterListItemData =
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

  private fun getSyncState() =
    sharedPreferencesHelper.read(SharedPreferenceKey.SYNC_STATUS.name, SyncState.InitialSync.value)

  init {
    identifierLookup()
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
            viewModelScope.launch(Dispatchers.IO) {
              if (
                syncConfigOfflineFirst(
                    configurationRegistry,
                    sharedPreferencesHelper,
                  )
                  .not()
              ) {
                return@launch
              }

              if (getSyncState() == SyncState.InitialSync.value) {
                sharedPreferencesHelper.write(
                  SharedPreferenceKey.SYNC_STATUS.name,
                  SyncState.CompletedInitialSync.value,
                )
                syncBroadcaster.runSync()
              }

              if (logicalIds(database.syncStrategyCacheDao).isNotEmpty()) syncBroadcaster.runSync()
            }
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
    sharedPreferencesHelper.read(SharedPreferenceKey.LAST_SYNC_TIMESTAMP.name, null).isNullOrBlank()

  fun progressMessage() =
    if (searchedText.value.isEmpty()) {
      ""
    } else {
      configurationRegistry.context.resources.getString(R.string.search_progress_message)
    }
}
