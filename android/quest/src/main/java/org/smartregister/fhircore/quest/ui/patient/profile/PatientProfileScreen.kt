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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Chip
import androidx.compose.material.ChipDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import java.util.Locale
import org.smartregister.fhircore.engine.R
import org.smartregister.fhircore.engine.domain.util.DataLoadState
import org.smartregister.fhircore.engine.ui.components.FormButton
import org.smartregister.fhircore.engine.ui.theme.PatientProfileSectionsBackgroundColor
import org.smartregister.fhircore.engine.ui.theme.WelcomeServiceBackToCareColor
import org.smartregister.fhircore.engine.ui.theme.WelcomeServiceHVLColor
import org.smartregister.fhircore.engine.ui.theme.WelcomeServiceNewlyDiagnosed
import org.smartregister.fhircore.engine.util.extension.asDdMmmYyyy
import org.smartregister.fhircore.quest.R as R2
import org.smartregister.fhircore.quest.ui.main.AppMainViewModel
import org.smartregister.fhircore.quest.ui.patient.profile.components.PersonalData
import org.smartregister.fhircore.quest.ui.patient.profile.components.ProfileActionableItem
import org.smartregister.fhircore.quest.ui.patient.profile.components.ProfileCard
import org.smartregister.fhircore.quest.ui.patient.profile.components.ProfileErrorCard
import org.smartregister.fhircore.quest.ui.shared.models.PatientProfileViewSection

@Composable
fun PatientProfileScreen(
  navController: NavHostController,
  modifier: Modifier = Modifier,
  appMainViewModel: AppMainViewModel,
  patientProfileViewModel: PatientProfileViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val profileViewDataState = patientProfileViewModel.patientProfileViewData.collectAsState()
  val profileViewData by remember { profileViewDataState }
  var showOverflowMenu by remember { mutableStateOf(false) }
  val viewState = patientProfileViewModel.patientProfileUiState.value
  val taskId by appMainViewModel.completedTaskId.collectAsState()
  val syncing by patientProfileViewModel.isSyncing.collectAsState()
  val tasksId = profileViewData.tasks.map { it.actionFormId }
  val loadingState by patientProfileViewModel.loadingState.collectAsState()
  val autoSyncEnabled by patientProfileViewModel.autoSyncOn.collectAsState()

  val launchQuestionnaireActivityForResults =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult(),
      onResult = {
        if (it.resultCode == Activity.RESULT_OK && it.data != null) {
          patientProfileViewModel.reFetch()
        }
      },
    )

  LaunchedEffect(taskId) { taskId?.let { patientProfileViewModel.reFetch() } }

  Scaffold(
    topBar = {
      Column(Modifier.fillMaxWidth()) {
        TopAppBar(
          title = { Text(stringResource(R.string.profile)) },
          navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }
          },
          actions = {
            AutoSyncButton(
              enabled = autoSyncEnabled,
              isSyncing = syncing,
              runSync = patientProfileViewModel::runSync,
            )
            IconButton(onClick = { patientProfileViewModel.reFetch() }, enabled = !syncing) {
              Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                tint = Color.White,
              )
            }
            IconButton(onClick = { showOverflowMenu = !showOverflowMenu }) {
              Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = null,
                tint = Color.White,
              )
            }
            DropdownMenu(
              expanded = showOverflowMenu,
              onDismissRequest = { showOverflowMenu = false },
            ) {
              viewState.visibleOverflowMenuItems().forEach {
                DropdownMenuItem(
                  onClick = {
                    showOverflowMenu = false
                    patientProfileViewModel.onEvent(
                      PatientProfileEvent.OverflowMenuClick(navController, context, it.id),
                    )
                  },
                  contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                  modifier =
                    modifier
                      .fillMaxWidth()
                      .background(
                        color =
                          if (it.confirmAction) {
                            it.titleColor.copy(alpha = 0.1f)
                          } else Color.Transparent,
                      ),
                ) {
                  when (it.id) {
                    R2.id.view_children -> {
                      Text(text = profileViewData.viewChildText, color = it.titleColor)
                    }
                    R2.id.view_guardians -> {
                      Text(
                        text = stringResource(it.titleResource, profileViewData.guardians.size),
                        color = it.titleColor,
                      )
                    }
                    else -> {
                      Text(text = stringResource(id = it.titleResource), color = it.titleColor)
                    }
                  }
                }
              }
            }
          },
        )
        if (loadingState is DataLoadState.Loading) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
      }
    },
  ) { innerPadding ->
    Column(
      modifier = modifier.fillMaxHeight().fillMaxWidth(),
    ) {
      Box(
        modifier = Modifier.padding(innerPadding).weight(2.0f),
      ) {
        Column(
          modifier =
            modifier
              .verticalScroll(rememberScrollState())
              .background(PatientProfileSectionsBackgroundColor),
        ) {
          // Personal Data: e.g. sex, age, dob
          if (tasksId.contains(PatientProfileViewModel.WELCOME_SERVICE_NEWLY_DIAGNOSED)) {
            PersonalData(profileViewData, color = WelcomeServiceNewlyDiagnosed)
          } else if (tasksId.contains(PatientProfileViewModel.WELCOME_SERVICE_HVL)) {
            PersonalData(profileViewData, color = WelcomeServiceHVLColor)
          } else if (tasksId.contains(PatientProfileViewModel.WELCOME_SERVICE_BACK_TO_CARE)) {
            PersonalData(profileViewData, color = WelcomeServiceBackToCareColor)
          } else {
            PersonalData(profileViewData)
          }

          ProfileErrorCard(profileViewData) {
            patientProfileViewModel.onEvent(PatientProfileEvent.OpenPatientFixer(navController))
          }

          // Patient tasks: List of tasks for the patients
          if (profileViewData.tasks.isNotEmpty()) {
            val appointmentDate = profileViewData.currentCarePlan?.period?.end
            ProfileCard(
              title = {
                Row(
                  modifier = Modifier.weight(1f),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text =
                      stringResource(R.string.next_appointment_date).uppercase(Locale.getDefault()),
                  )
                  if (appointmentDate != null) Text(text = appointmentDate.asDdMmmYyyy())
                }
              },
              onActionClick = {},
              showSeeAll = profileViewData.showListsHighlights,
              profileViewSection = PatientProfileViewSection.TASKS,
            ) {
              profileViewData.tasks.forEach {
                ProfileActionableItem(
                  if (loadingState is DataLoadState.Loading) it.copy(actionFormId = null) else it,
                  onActionClick = { taskFormId, taskId ->
                    launchQuestionnaireActivityForResults.launch(
                      patientProfileViewModel.createLaunchTaskIntent(
                        context = context,
                        taskFormId = taskFormId,
                        taskId = taskId,
                      ),
                    )
                  },
                )
              }
            }
          }

          // Forms: Loaded for quest app
          if (profileViewData.forms.isNotEmpty()) {
            ProfileCard(
              title = stringResource(R.string.forms),
              onActionClick = { patientProfileViewModel.onEvent(PatientProfileEvent.SeeAll(it)) },
              profileViewSection = PatientProfileViewSection.FORMS,
            ) {
              profileViewData.forms.forEach {
                FormButton(
                  formButtonData = it,
                  onFormClick = { questionnaireId, _ ->
                    launchQuestionnaireActivityForResults.launch(
                      patientProfileViewModel.createLaunchQuestionnaireIntent(
                        context = context,
                        questionnaireId = questionnaireId,
                      ),
                    )
                  },
                )
              }
            }
          }

          // Medical History: Show medication history for the patient
          // TODO add handled events for all items action click
          if (profileViewData.medicalHistoryData.isNotEmpty()) {
            ProfileCard(
              title = stringResource(R.string.medical_history),
              onActionClick = { patientProfileViewModel.onEvent(PatientProfileEvent.SeeAll(it)) },
              profileViewSection = PatientProfileViewSection.MEDICAL_HISTORY,
            ) {
              profileViewData.medicalHistoryData.forEach {
                ProfileActionableItem(it, onActionClick = { _, _ -> })
              }
            }
          }

          // Upcoming Services: Display upcoming services (or tasks) for the patient
          if (profileViewData.upcomingServices.isNotEmpty()) {
            ProfileCard(
              title = stringResource(R.string.upcoming_services),
              onActionClick = { patientProfileViewModel.onEvent(PatientProfileEvent.SeeAll(it)) },
              profileViewSection = PatientProfileViewSection.UPCOMING_SERVICES,
            ) {
              profileViewData.upcomingServices.forEach {
                ProfileActionableItem(it, onActionClick = { _, _ -> })
              }
            }
          }

          // Service Card: Display other vital information for ANC/PNC
          if (profileViewData.ancCardData.isNotEmpty()) {
            ProfileCard(
              title = stringResource(R.string.service_card),
              onActionClick = { patientProfileViewModel.onEvent(PatientProfileEvent.SeeAll(it)) },
              profileViewSection = PatientProfileViewSection.SERVICE_CARD,
            ) {
              profileViewData.ancCardData.forEach {
                ProfileActionableItem(it, onActionClick = { _, _ -> })
              }
            }
          }
        }
      }

      //  Finish visit
      if (profileViewData.currentCarePlan != null && profileViewData.tasks.isNotEmpty()) {
        Button(
          modifier = Modifier.fillMaxWidth().padding(0.dp),
          shape = RectangleShape,
          onClick = {
            launchQuestionnaireActivityForResults.launch(
              patientProfileViewModel.createLaunchTaskIntent(
                context = context,
                taskFormId = PatientProfileViewModel.PATIENT_FINISH_VISIT,
              ),
            )
          },
          enabled = profileViewData.tasksCompleted && loadingState !is DataLoadState.Loading,
        ) {
          Text(
            modifier = Modifier.padding(10.dp),
            text = stringResource(id = R.string.finish).uppercase(),
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AutoSyncButton(enabled: Boolean, isSyncing: Boolean, runSync: () -> Unit) {
  if (!enabled) {
    Chip(
      onClick = runSync,
      colors =
        if (isSyncing) {
          ChipDefaults.chipColors()
        } else
          ChipDefaults.chipColors(
            backgroundColor = MaterialTheme.colors.error,
            contentColor = MaterialTheme.colors.onError,
          ),
    ) {
      if (isSyncing) {
        Text(text = "Syncing...")
      } else {
        Text(text = "Auto sync off")
      }
    }
  }
}
