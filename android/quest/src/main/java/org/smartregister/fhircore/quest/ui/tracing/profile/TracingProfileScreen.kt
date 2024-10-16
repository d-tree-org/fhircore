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

package org.smartregister.fhircore.quest.ui.tracing.profile

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import org.hl7.fhir.r4.model.RelatedPerson
import org.smartregister.fhircore.engine.R
import org.smartregister.fhircore.engine.domain.model.TracingAttempt
import org.smartregister.fhircore.engine.domain.util.DataLoadState
import org.smartregister.fhircore.engine.ui.theme.LoginButtonColor
import org.smartregister.fhircore.engine.ui.theme.LoginFieldBackgroundColor
import org.smartregister.fhircore.engine.ui.theme.PatientProfileSectionsBackgroundColor
import org.smartregister.fhircore.engine.ui.theme.StatusTextColor
import org.smartregister.fhircore.engine.ui.theme.SuccessColor
import org.smartregister.fhircore.engine.util.annotation.ExcludeFromJacocoGeneratedReport
import org.smartregister.fhircore.engine.util.extension.asDdMmYyyy
import org.smartregister.fhircore.engine.util.extension.safeSubList
import org.smartregister.fhircore.quest.R as R2
import org.smartregister.fhircore.quest.ui.main.AppMainViewModel
import org.smartregister.fhircore.quest.ui.shared.models.ProfileViewData
import org.smartregister.fhircore.quest.ui.tracing.components.InfoBoxItem
import org.smartregister.fhircore.quest.ui.tracing.components.OutlineCard

@Composable
fun TracingProfileScreen(
  navController: NavHostController,
  modifier: Modifier = Modifier,
  viewModel: TracingProfileViewModel = hiltViewModel(),
  appViewModel: AppMainViewModel = hiltViewModel(),
) {
  val taskId by appViewModel.completedTaskId.collectAsState()

  LaunchedEffect(taskId) { taskId?.let { viewModel.reSync() } }

  TracingProfilePage(
    navController,
    modifier = modifier,
    tracingProfileViewModel = viewModel,
    onBackPress = { navController.navigateUp() },
  )
}

@Composable
fun TracingProfilePage(
  navController: NavHostController,
  modifier: Modifier = Modifier,
  onBackPress: () -> Unit,
  tracingProfileViewModel: TracingProfileViewModel,
) {
  val context = LocalContext.current
  val profileViewDataState = tracingProfileViewModel.patientProfileViewData.collectAsState()
  val profileViewData by remember { profileViewDataState }
  var showOverflowMenu by remember { mutableStateOf(false) }
  val viewState = tracingProfileViewModel.patientTracingProfileUiState.value
  val loadingState by tracingProfileViewModel.loadingState.collectAsState()

  val launchQuestionnaireActivityForResults =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult(),
      onResult = {
        if (it.resultCode == Activity.RESULT_OK && it.data != null) {
          tracingProfileViewModel.reSync()
        }
      },
    )

  LaunchedEffect(profileViewData) {
    if (profileViewData.logicalId.isNotBlank() && profileViewData.hasFinishedAttempts) {
      onBackPress()
    }
  }

  Scaffold(
    topBar = {
      Column(Modifier.fillMaxWidth()) {
        TopAppBar(
          title = { Text(stringResource(id = R2.string.patient_details)) },
          navigationIcon = {
            IconButton(onClick = { onBackPress() }) { Icon(Icons.Filled.ArrowBack, null) }
          },
          actions = {
            IconButton(onClick = { tracingProfileViewModel.reSync() }) {
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
                    tracingProfileViewModel.onEvent(
                      TracingProfileEvent.OverflowMenuClick(
                        navController = navController,
                        context,
                        it.id,
                      ),
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
                  Text(text = stringResource(id = it.titleResource), color = it.titleColor)
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
    bottomBar = {
      Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth()) {
        val hasFinishedAttempts = profileViewData.hasFinishedAttempts
        Button(
          colors =
            ButtonDefaults.buttonColors(
              backgroundColor = LoginButtonColor,
              LoginFieldBackgroundColor,
            ),
          enabled = !hasFinishedAttempts && loadingState !is DataLoadState.Loading,
          onClick = {
            if (!hasFinishedAttempts && loadingState !is DataLoadState.Loading) {
              launchQuestionnaireActivityForResults.launch(
                tracingProfileViewModel.createOutcomesIntent(context = context),
              )
            }
          },
          modifier = modifier.fillMaxWidth(),
        ) {
          Text(
            color = Color.White,
            text = stringResource(id = R2.string.tracing_outcomes),
            modifier = modifier.padding(8.dp),
          )
        }
      }
    },
  ) { innerPadding ->
    Column(Modifier.fillMaxSize()) {
      if (loadingState is DataLoadState.Error) {
        Column(
          Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            fontSize = 14.sp,
            color = MaterialTheme.colors.error,
            text =
              stringResource(
                id = R.string.error_fetching_user_details,
              ),
            modifier =
              Modifier.wrapContentWidth()
                .padding(vertical = 10.dp)
                .align(Alignment.CenterHorizontally),
          )
          Button(onClick = { tracingProfileViewModel.reSync() }) { Text(text = "Retry") }
        }
      }
      TracingProfilePageView(
        innerPadding = innerPadding,
        profileViewData = profileViewData,
        onCall = {
          tracingProfileViewModel.onEvent(
            TracingProfileEvent.CallPhoneNumber(navController, context, it),
          )
        },
      ) {
        val historyId = it.historyId
        if (historyId != null) {
          tracingProfileViewModel.onEvent(
            TracingProfileEvent.OpenTracingOutcomeScreen(navController, context, historyId),
          )
        } else {
          Toast.makeText(context, "No Tracing outcomes recorded", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }
}

@Composable
fun TracingProfilePageView(
  modifier: Modifier = Modifier,
  innerPadding: PaddingValues = PaddingValues(all = 0.dp),
  profileViewData: ProfileViewData.TracingProfileData = ProfileViewData.TracingProfileData(),
  onCall: (String) -> Unit,
  onCurrentAttemptClicked: (TracingAttempt) -> Unit,
) {
  Column(
    modifier = modifier.fillMaxHeight().fillMaxWidth().padding(innerPadding),
  ) {
    Box(
      modifier = Modifier.padding(5.dp).weight(2.0f),
    ) {
      Column(
        modifier =
          modifier
            .verticalScroll(rememberScrollState())
            .background(PatientProfileSectionsBackgroundColor),
      ) {
        // Personal Data: e.g. sex, age, dob
        PatientInfo(profileViewData)
        Spacer(modifier = modifier.height(20.dp))
        // Tracing Visit Due // pull tracingTask -> executionPeriod -> end
        TracingVisitDue(profileViewData.dueDate)
        // TracingVisitDue(profileViewData.tracingTask.executionPeriod.end.asDdMmYyyy())
        Spacer(modifier = modifier.height(20.dp))
        // Tracing Reason
        if (profileViewData.currentAttempt != null) {
          TracingReasonCard(
            currentAttempt = profileViewData.currentAttempt,
            displayForHomeTrace = profileViewData.isHomeTracing!!,
            onClick = onCurrentAttemptClicked,
          )
        }
        Spacer(modifier = modifier.height(20.dp))
        // Tracing Patient address/contact
        if (profileViewData.isHomeTracing != null) {
          TracingContactAddress(
            profileViewData,
            displayForHomeTrace = profileViewData.isHomeTracing,
            onCall = onCall,
          )
        }
        Spacer(modifier = modifier.height(20.dp))
        TracingGuardianAddress(
          guardiansRelatedPersonResource = profileViewData.guardiansRelatedPersonResource,
          onCall = onCall,
        )
      }
    }
  }
}

@Preview(showBackground = true)
@ExcludeFromJacocoGeneratedReport
@Composable
fun TracingScreenPreview() {
  TracingProfilePageView(modifier = Modifier, onCall = {}) {}
}

@Composable
fun PatientInfo(
  patientProfileViewData: ProfileViewData.TracingProfileData,
  modifier: Modifier = Modifier,
) {
  Card(elevation = 3.dp, modifier = modifier.fillMaxWidth()) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
      InfoBoxItem(title = stringResource(R2.string.name), value = patientProfileViewData.name)
      InfoBoxItem(title = stringResource(R.string.age), value = patientProfileViewData.age)
      InfoBoxItem(title = stringResource(R.string.sex), value = patientProfileViewData.sex)
      if (patientProfileViewData.identifier != null) {
        val idKeyValue: String
        if (patientProfileViewData.showIdentifierInProfile) {
          idKeyValue =
            stringResource(
              R.string.idKeyValue,
              patientProfileViewData.identifierKey,
              patientProfileViewData.identifier.ifEmpty {
                stringResource(R.string.identifier_unassigned)
              },
            )
          Text(
            text = idKeyValue,
            color = StatusTextColor,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun TracingReasonItem(
  title: String,
  value: String,
  modifier: Modifier = Modifier,
  verticalRenderOrientation: Boolean = false,
) {
  if (verticalRenderOrientation) {
    Column(modifier = modifier.padding(4.dp)) {
      Row(modifier = modifier.padding(bottom = 4.dp)) {
        Text(text = title, modifier)
        Text(
          text = ":",
          modifier.padding(end = 4.dp),
        )
      }
      Text(text = value, color = StatusTextColor)
    }
  } else {
    Row(modifier = modifier.padding(4.dp)) {
      Text(text = title, modifier)
      Text(text = ":", modifier.padding(end = 4.dp), color = StatusTextColor)
      Text(text = value, color = StatusTextColor)
    }
  }
}

@Composable
private fun TracingVisitDue(dueDate: String?, modifier: Modifier = Modifier) {
  OutlineCard(
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = modifier.padding(6.dp, 8.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = stringResource(R2.string.tracing_visit_due),
        modifier.padding(bottom = 4.dp),
        color = StatusTextColor,
        fontSize = 18.sp,
      )
      Text(text = dueDate ?: "N/A", fontSize = 18.sp)
    }
  }
}

@Composable
private fun TracingReasonCard(
  currentAttempt: TracingAttempt,
  modifier: Modifier = Modifier,
  displayForHomeTrace: Boolean = false,
  onClick: (TracingAttempt) -> Unit,
) {
  OutlineCard(
    modifier = modifier.fillMaxWidth().clickable { onClick(currentAttempt) },
  ) {
    Column(modifier = modifier.padding(horizontal = 4.dp)) {
      TracingReasonItem(
        title = stringResource(R2.string.reason_for_trace),
        value =
          if (currentAttempt.reasons.isNotEmpty()) {
            currentAttempt.reasons.joinToString(separator = ",") { it }
          } else {
            "None"
          },
      )
      TracingReasonItem(
        title =
          if (displayForHomeTrace) {
            stringResource(R2.string.last_home_trace_outcome)
          } else {
            stringResource(R2.string.last_phone_trace_outcome)
          },
        value = currentAttempt.outcome.ifBlank { "None" },
        verticalRenderOrientation = true,
      )
      TracingReasonItem(
        title = stringResource(R2.string.date_of_last_attempt),
        value = currentAttempt.lastAttempt?.asDdMmYyyy() ?: "None",
      )
      TracingReasonItem(
        title = stringResource(R2.string.number_of_attempts),
        value = (currentAttempt.numberOfAttempts).toString(),
      )
    }
  }
}

@Composable
private fun TracingContactAddress(
  patientProfileViewData: ProfileViewData.TracingProfileData,
  modifier: Modifier = Modifier,
  displayForHomeTrace: Boolean,
  onCall: (String) -> Unit,
) {
  OutlineCard(
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = modifier.padding(horizontal = 4.dp).fillMaxWidth(),
    ) {
      if (displayForHomeTrace) {
        TracingReasonItem(
          title = stringResource(R2.string.patient_district),
          value = patientProfileViewData.addressDistrict,
        )
        TracingReasonItem(
          title = stringResource(R2.string.patient_tracing_catchment),
          value = patientProfileViewData.addressTracingCatchment,
        )
        TracingReasonItem(
          title = stringResource(R2.string.patient_physcal_locator),
          value = patientProfileViewData.addressPhysicalLocator,
        )
      } else {
        for (contact in patientProfileViewData.phoneContacts) {
          TracingReasonItem(
            title = stringResource(R2.string.patient_phone_number, 1),
            value = contact.number,
          )
          TracingReasonItem(
            title = stringResource(R2.string.patient_phone_owner, 1),
            value = contact.owner,
          )
          CallRow { onCall(contact.number) }
        }
      }
    }
  }
}

@Composable
private fun TracingGuardianAddress(
  guardiansRelatedPersonResource: List<RelatedPerson>,
  modifier: Modifier = Modifier,
  onCall: (String) -> Unit,
) {
  guardiansRelatedPersonResource.safeSubList(0..1).mapIndexed { i, guardian ->
    OutlineCard(
      modifier = modifier.fillMaxWidth(),
    ) {
      Column(modifier = modifier.padding(horizontal = 4.dp)) {
        guardian.relationshipFirstRep?.codingFirstRep?.display?.let {
          TracingReasonItem(title = stringResource(R2.string.guardian_relation), value = it)
        }
        TracingReasonItem(
          title = stringResource(R2.string.guardian_phone_number, i + 1),
          value = guardian.telecomFirstRep.value,
        )
        TracingReasonItem(
          title = stringResource(R2.string.guardian_phone_owner, i + 1),
          value = "Guardian ${i + 1}",
        )
        CallRow { onCall(guardian.telecomFirstRep.value) }
      }
    }
  }
}

@Composable
private fun CallRow(
  onClick: () -> Unit,
) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    TextButton(onClick = onClick) {
      Text(
        text = stringResource(R2.string.call),
        textAlign = TextAlign.End,
        fontSize = 14.sp,
        color = SuccessColor,
      )
    }
  }
}
