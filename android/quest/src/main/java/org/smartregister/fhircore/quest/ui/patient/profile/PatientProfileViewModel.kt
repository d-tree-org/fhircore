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

package org.smartregister.fhircore.quest.ui.patient.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.android.fhir.datacapture.extensions.logicalId
import com.google.android.fhir.sync.SyncJobStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.ResourceType
import org.smartregister.fhircore.engine.appfeature.AppFeature
import org.smartregister.fhircore.engine.appfeature.model.HealthModule
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.configuration.app.ApplicationConfiguration
import org.smartregister.fhircore.engine.data.local.register.AppRegisterRepository
import org.smartregister.fhircore.engine.domain.model.HealthStatus
import org.smartregister.fhircore.engine.domain.model.ProfileData
import org.smartregister.fhircore.engine.domain.util.DataLoadState
import org.smartregister.fhircore.engine.domain.util.PaginationConstant
import org.smartregister.fhircore.engine.sync.SyncBroadcaster
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireActivity
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireType
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import org.smartregister.fhircore.engine.util.extension.asReference
import org.smartregister.fhircore.engine.util.extension.isGuardianVisit
import org.smartregister.fhircore.quest.R
import org.smartregister.fhircore.quest.data.patient.model.PatientPagingSourceState
import org.smartregister.fhircore.quest.navigation.MainNavigationScreen
import org.smartregister.fhircore.quest.navigation.NavigationArg
import org.smartregister.fhircore.quest.navigation.OverflowMenuFactory
import org.smartregister.fhircore.quest.navigation.OverflowMenuHost
import org.smartregister.fhircore.quest.ui.family.remove.member.RemoveFamilyMemberQuestionnaireActivity
import org.smartregister.fhircore.quest.ui.patient.fix.FixPatientViewModel
import org.smartregister.fhircore.quest.ui.patient.fix.FixStartState
import org.smartregister.fhircore.quest.ui.patient.profile.childcontact.ChildContactPagingSource
import org.smartregister.fhircore.quest.ui.patient.profile.tranfer.TransferOutActivity
import org.smartregister.fhircore.quest.ui.shared.models.ProfileViewData
import org.smartregister.fhircore.quest.ui.shared.models.RegisterViewData
import org.smartregister.fhircore.quest.util.mappers.ProfileViewDataMapper
import org.smartregister.fhircore.quest.util.mappers.RegisterViewDataMapper
import timber.log.Timber

@HiltViewModel
class PatientProfileViewModel
@Inject
constructor(
  savedStateHandle: SavedStateHandle,
  val syncBroadcaster: SyncBroadcaster,
  private val overflowMenuFactory: OverflowMenuFactory,
  val registerRepository: AppRegisterRepository,
  val configurationRegistry: ConfigurationRegistry,
  private val profileViewDataMapper: ProfileViewDataMapper,
  val registerViewDataMapper: RegisterViewDataMapper,
  val sharedPreferences: SharedPreferencesHelper,
) : ViewModel() {

  val appFeatureName = savedStateHandle.get<String>(NavigationArg.FEATURE)
  val healthModule =
    savedStateHandle.get<HealthModule>(NavigationArg.HEALTH_MODULE) ?: HealthModule.DEFAULT
  val patientId = savedStateHandle.get<String>(NavigationArg.PATIENT_ID) ?: ""
  val familyId = savedStateHandle.get<String>(NavigationArg.FAMILY_ID)

  // TODO: replace later with actual implementation from the engine
  val isSyncing = MutableStateFlow(false)

  var patientProfileUiState: MutableState<PatientProfileUiState> =
    mutableStateOf(
      PatientProfileUiState(
        overflowMenuFactory.retrieveOverflowMenuItems(OverflowMenuHost.PATIENT_PROFILE),
      ),
    )

  private val _patientProfileViewDataFlow =
    MutableStateFlow(ProfileViewData.PatientProfileViewData())
  val patientProfileViewData: StateFlow<ProfileViewData.PatientProfileViewData>
    get() = _patientProfileViewDataFlow.asStateFlow()

  private var patientProfileData: ProfileData? = null

  private val applicationConfiguration: ApplicationConfiguration
    get() = configurationRegistry.getAppConfigs()

  private val isClientVisit: MutableState<Boolean> = mutableStateOf(true)

  val loadingState = MutableStateFlow<DataLoadState<Boolean>>(DataLoadState.Idle)

  val autoSyncOn = MutableStateFlow(true)

  init {
    syncBroadcaster.registerSyncListener(
      { state ->
        when (state) {
          is SyncJobStatus.Succeeded,
          is SyncJobStatus.Failed, -> {
            isSyncing.value = false
            fetchPatientProfileDataWithChildren()
          }
          is SyncJobStatus.Started -> {
            isSyncing.value = true
          }
          is SyncJobStatus.InProgress -> {}
          else -> {
            isSyncing.value = false
          }
        }
      },
      viewModelScope,
    )

    fetchPatientProfileDataWithChildren()
    checkAutoSyncStatus()
  }

  private fun checkAutoSyncStatus() {
    autoSyncOn.value = sharedPreferences.read(SharedPreferenceKey.SYNC_ON_SAVE.name, true)
  }

  fun runSync() {
    syncBroadcaster.runSync()
  }

  fun getOverflowMenuHostByPatientType(healthStatus: HealthStatus): OverflowMenuHost {
    return when (healthStatus) {
      HealthStatus.NEWLY_DIAGNOSED_CLIENT -> OverflowMenuHost.NEWLY_DIAGNOSED_PROFILE
      HealthStatus.CLIENT_ALREADY_ON_ART -> OverflowMenuHost.ART_CLIENT_PROFILE
      HealthStatus.EXPOSED_INFANT -> OverflowMenuHost.EXPOSED_INFANT_PROFILE
      HealthStatus.CHILD_CONTACT -> OverflowMenuHost.CHILD_CONTACT_PROFILE
      HealthStatus.SEXUAL_CONTACT -> OverflowMenuHost.SEXUAL_CONTACT_PROFILE
      HealthStatus.SIBLING_CONTACT -> OverflowMenuHost.SIBLING_CONTACT_PROFILE
      HealthStatus.BIOLOGICAL_PARENT_CONTACT -> OverflowMenuHost.BIOLOGICAL_PARENT_CONTACT_PROFILE
      HealthStatus.SOCIAL_NETWORK_CONTACT -> OverflowMenuHost.SOCIAL_NETWORK_CONTACT_PROFILE
      HealthStatus.COMMUNITY_POSITIVE -> OverflowMenuHost.COMMUNITY_POSITIVE_PROFILE
      else -> OverflowMenuHost.PATIENT_PROFILE
    }
  }

  fun refreshOverFlowMenu(healthModule: HealthModule, patientProfile: ProfileData) {
    if (healthModule == HealthModule.HIV) {
      patientProfileUiState.value =
        PatientProfileUiState(
          overflowMenuFactory.retrieveOverflowMenuItems(
            getOverflowMenuHostByPatientType(
              (patientProfile as ProfileData.HivProfileData).healthStatus,
            ),
          ),
        )
    }
  }

  fun reFetch() {
    fetchPatientProfileDataWithChildren()
  }

  private fun filterGuardianVisitTasks() {
    if (patientProfileData != null) {
      val hivPatientProfileData = patientProfileData as ProfileData.HivProfileData
      val newProfileData =
        hivPatientProfileData.copy(
          tasks =
            hivPatientProfileData.tasks.filter {
              it.task.isGuardianVisit(applicationConfiguration.taskFilterTagViaMetaCodingSystem)
            },
        )
      _patientProfileViewDataFlow.value =
        profileViewDataMapper.transformInputToOutputModel(newProfileData)
          as ProfileViewData.PatientProfileViewData
    }
  }

  fun undoGuardianVisitTasksFilter() {
    if (patientProfileData != null) {
      _patientProfileViewDataFlow.value =
        profileViewDataMapper.transformInputToOutputModel(patientProfileData!!)
          as ProfileViewData.PatientProfileViewData
    }
  }

  fun onEvent(event: PatientProfileEvent) {
    val profile = patientProfileViewData.value

    when (event) {
      is PatientProfileEvent.LoadQuestionnaire ->
        QuestionnaireActivity.launchQuestionnaire(
          event.context,
          questionnaireId = event.questionnaireId,
          clientIdentifier = patientId,
          populationResources = profile.populationResources,
        )
      is PatientProfileEvent.SeeAll -> {
        // TODO(View all records in this category e.g. all medical history, tasks etc)
      }
      is PatientProfileEvent.OverflowMenuClick -> {
        when (event.menuId) {
          R.id.individual_details ->
            QuestionnaireActivity.launchQuestionnaire(
              event.context,
              questionnaireId = FAMILY_MEMBER_REGISTER_FORM,
              clientIdentifier = patientId,
              questionnaireType = QuestionnaireType.EDIT,
            )
          R.id.guardian_visit -> {
            isClientVisit.value = false
            handleVisitType(false)
          }
          R.id.client_visit -> {
            isClientVisit.value = true
            handleVisitType(true)
          }
          R.id.view_guardians -> {
            val commonParams =
              NavigationArg.bindArgumentsOf(
                Pair(NavigationArg.FEATURE, AppFeature.PatientManagement.name),
                Pair(NavigationArg.HEALTH_MODULE, HealthModule.HIV),
              )

            event.navController.navigate(
              route = "${MainNavigationScreen.PatientGuardians.route}/$patientId$commonParams",
            ) {
              launchSingleTop = true
            }
          }
          R.id.view_family -> {
            familyId?.let {
              val urlParams =
                NavigationArg.bindArgumentsOf(
                  Pair(NavigationArg.FEATURE, AppFeature.HouseholdManagement.name),
                  Pair(NavigationArg.HEALTH_MODULE, HealthModule.FAMILY),
                  Pair(NavigationArg.PATIENT_ID, it),
                )
              event.navController.navigate(
                route = MainNavigationScreen.FamilyProfile.route + urlParams,
              )
            }
          }
          R.id.view_children -> {
            patientId.let {
              val urlParams =
                NavigationArg.bindArgumentsOf(
                  Pair(NavigationArg.FEATURE, AppFeature.PatientManagement.name),
                  Pair(NavigationArg.HEALTH_MODULE, HealthModule.HIV),
                  Pair(NavigationArg.PATIENT_ID, it),
                )
              event.navController.navigate(
                route = MainNavigationScreen.ViewChildContacts.route + urlParams,
              )
            }
          }
          R.id.remove_family_member ->
            RemoveFamilyMemberQuestionnaireActivity.launchQuestionnaire(
              event.context,
              questionnaireId = REMOVE_FAMILY_FORM,
              clientIdentifier = patientId,
              intentBundle = bundleOf(Pair(NavigationArg.FAMILY_ID, familyId)),
            )
          R.id.record_as_anc ->
            QuestionnaireActivity.launchQuestionnaire(
              event.context,
              questionnaireId = ANC_ENROLLMENT_FORM,
              clientIdentifier = patientId,
              questionnaireType = QuestionnaireType.DEFAULT,
            )
          R.id.edit_profile ->
            QuestionnaireActivity.launchQuestionnaire(
              event.context,
              questionnaireId = EDIT_PROFILE_FORM,
              clientIdentifier = patientId,
              questionnaireType = QuestionnaireType.DEFAULT,
              populationResources = profile.populationResources,
            )
          R.id.viral_load_results ->
            QuestionnaireActivity.launchQuestionnaire(
              event.context,
              questionnaireId = VIRAL_LOAD_RESULTS_FORM,
              clientIdentifier = patientId,
              questionnaireType = QuestionnaireType.DEFAULT,
              populationResources = profile.populationResources,
            )
          R.id.hiv_test_and_results ->
            QuestionnaireActivity.launchQuestionnaire(
              event.context,
              questionnaireId = HIV_TEST_AND_RESULTS_FORM,
              clientIdentifier = patientId,
              questionnaireType = QuestionnaireType.DEFAULT,
              populationResources = profile.populationResources,
            )
          R.id.hiv_test_and_next_appointment ->
            QuestionnaireActivity.launchQuestionnaire(
              event.context,
              questionnaireId = HIV_TEST_AND_NEXT_APPOINTMENT_FORM,
              clientIdentifier = patientId,
              questionnaireType = QuestionnaireType.DEFAULT,
              populationResources = profile.populationResources,
            )
          R.id.patient_transfer_out -> {
            //            QuestionnaireActivity.launchQuestionnaire(
            //              event.context,
            //              questionnaireId = PATIENT_TRANSFER_OUT,
            //              clientIdentifier = patientId,
            //              questionnaireType = QuestionnaireType.DEFAULT,
            //              populationResources = profile.populationResources,
            //            )
            patientId.let { TransferOutActivity.launch(event.context, patientId) }
          }
          R.id.patient_change_status ->
            QuestionnaireActivity.launchQuestionnaire(
              event.context,
              questionnaireId = PATIENT_CHANGE_STATUS,
              clientIdentifier = patientId,
              questionnaireType = QuestionnaireType.DEFAULT,
              populationResources = profile.populationResources,
            )
          else -> {}
        }
      }
      is PatientProfileEvent.OpenTaskForm ->
        QuestionnaireActivity.launchQuestionnaireForResult(
          event.context as Activity,
          questionnaireId = event.taskFormId,
          clientIdentifier = patientId,
          backReference = event.taskId.asReference(ResourceType.Task).reference,
          populationResources = profile.populationResources,
        )
      is PatientProfileEvent.FinishVisit ->
        QuestionnaireActivity.launchQuestionnaireForResult(
          event.context as Activity,
          questionnaireId = event.formId,
          clientIdentifier = patientId,
          populationResources = profile.populationResources,
        )
      is PatientProfileEvent.OpenChildProfile -> {
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
      is PatientProfileEvent.OpenPatientFixer -> {
        if (patientProfileData != null && patientProfileData is ProfileData.HivProfileData) {
          val urlParams =
            NavigationArg.bindArgumentsOf(
              Pair(NavigationArg.FEATURE, AppFeature.PatientManagement.name),
              Pair(NavigationArg.HEALTH_MODULE, healthModule),
              Pair(NavigationArg.PATIENT_ID, patientId),
              Pair(FixPatientViewModel.NAVIGATION_ARG_START, FixStartState.StartFix.name),
              Pair(
                FixPatientViewModel.NAVIGATION_ARG_CARE_PLAN,
                (patientProfileData as ProfileData.HivProfileData).currentCarePlan?.logicalId,
              ),
            )
          event.navController.navigate(
            route = MainNavigationScreen.FixPatientProfile.route + urlParams,
          )
        }
      }
    }
  }

  fun createLaunchTaskIntent(context: Context, taskFormId: String, taskId: String? = null): Intent {
    val profile = patientProfileViewData.value
    return QuestionnaireActivity.createQuestionnaireResultIntent(
      context as Activity,
      questionnaireId = taskFormId,
      clientIdentifier = patientId,
      backReference = taskId?.asReference(ResourceType.Task)?.reference,
      populationResources = profile.populationResources,
    )
  }

  fun createLaunchQuestionnaireIntent(context: Context, questionnaireId: String): Intent {
    val profile = patientProfileViewData.value
    return QuestionnaireActivity.createQuestionnaireIntent(
      context = context,
      questionnaireId = questionnaireId,
      clientIdentifier = patientId,
      populationResources = profile.populationResources,
    )
  }

  private fun handleVisitType(isClientVisit: Boolean) {
    if (isClientVisit) {
      val updatedMenuItems =
        patientProfileUiState.value.overflowMenuItems.map {
          when (it.id) {
            R.id.guardian_visit -> it.apply { hidden = false }
            R.id.client_visit -> it.apply { hidden = true }
            else -> it
          }
        }
      patientProfileUiState.value =
        patientProfileUiState.value.copy(overflowMenuItems = updatedMenuItems)
      undoGuardianVisitTasksFilter()
    } else {
      val updatedMenuItems =
        patientProfileUiState.value.overflowMenuItems.map {
          when (it.id) {
            R.id.guardian_visit -> it.apply { hidden = true }
            R.id.client_visit -> it.apply { hidden = false }
            else -> it
          }
        }
      patientProfileUiState.value =
        patientProfileUiState.value.copy(overflowMenuItems = updatedMenuItems)
      filterGuardianVisitTasks()
    }
  }

  private fun fetchPatientProfileDataWithChildren() {
    if (patientId.isNotEmpty()) {
      viewModelScope.launch {
        try {
          loadingState.value = DataLoadState.Loading
          registerRepository.loadPatientProfileData(appFeatureName, healthModule, patientId)?.let {
            patientProfileData = it
            _patientProfileViewDataFlow.value =
              profileViewDataMapper.transformInputToOutputModel(it)
                as ProfileViewData.PatientProfileViewData
            refreshOverFlowMenu(healthModule = healthModule, patientProfile = it)
            paginateChildrenRegisterData(true)
            handleVisitType(isClientVisit.value)
          }
          loadingState.value = DataLoadState.Success(true)
        } catch (e: Exception) {
          Timber.e(e)
          loadingState.value = DataLoadState.Error(e)
        }
      }
    }
  }

  val paginatedChildrenRegisterData:
    MutableStateFlow<Flow<PagingData<RegisterViewData.ListItemView>>> =
    MutableStateFlow(emptyFlow())

  private fun paginateChildrenRegisterData(loadAll: Boolean = true) {
    paginatedChildrenRegisterData.value =
      getPager(appFeatureName, healthModule, loadAll).flow.cachedIn(viewModelScope)
  }

  private fun getPager(
    appFeatureName: String?,
    healthModule: HealthModule,
    loadAll: Boolean = true,
  ): Pager<Int, RegisterViewData.ListItemView> =
    Pager(
      config =
        PagingConfig(
          pageSize = PaginationConstant.DEFAULT_PAGE_SIZE,
          initialLoadSize = PaginationConstant.DEFAULT_INITIAL_LOAD_SIZE,
        ),
      pagingSourceFactory = {
        ChildContactPagingSource(
            patientProfileViewData.value.otherPatients,
            registerRepository,
            registerViewDataMapper,
          )
          .apply {
            setPatientPagingSourceState(
              PatientPagingSourceState(
                appFeatureName = appFeatureName,
                healthModule = healthModule,
                loadAll = loadAll,
                currentPage = 0,
              ),
            )
          }
      },
    )

  companion object {
    const val REMOVE_FAMILY_FORM = "remove-family"
    const val FAMILY_MEMBER_REGISTER_FORM = "family-member-registration"
    const val ANC_ENROLLMENT_FORM = "anc-patient-registration"
    const val EDIT_PROFILE_FORM = "patient-edit-profile"
    const val VIRAL_LOAD_RESULTS_FORM = "art-client-viral-load-test-results"
    const val HIV_TEST_AND_RESULTS_FORM = "exposed-infant-hiv-test-and-results"
    const val HIV_TEST_AND_NEXT_APPOINTMENT_FORM =
      "contact-and-community-positive-hiv-test-and-next-appointment"
    const val PATIENT_FINISH_VISIT = "patient-finish-visit"
    const val PATIENT_CHANGE_STATUS = "patient-change-status"
    const val PATIENT_TRANSFER_OUT = "patient-transfer-out"
    const val WELCOME_SERVICE_BACK_TO_CARE = "art-client-welcome-service-back-to-care"
    const val WELCOME_SERVICE_HVL = "art-client-welcome-service-high-or-detectable-viral-load"
    const val WELCOME_SERVICE_NEWLY_DIAGNOSED = "art-client-welcome-service-newly-diagnosed"
  }
}
