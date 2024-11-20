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

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.EventCallback
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.Progress
import org.smartregister.fhircore.engine.data.remote.resource.syncStrategy.utils.SyncState
import org.smartregister.fhircore.engine.ui.components.register.LoaderDialog
import org.smartregister.fhircore.engine.ui.components.register.RegisterHeader
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireActivity
import org.smartregister.fhircore.engine.util.extension.decodeResourceFromString
import org.smartregister.fhircore.engine.util.extension.extractId
import org.smartregister.fhircore.quest.ui.components.RegisterFooter
import org.smartregister.fhircore.quest.ui.components.RegisterList
import org.smartregister.fhircore.quest.ui.main.components.TopScreenSection
import org.smartregister.fhircore.quest.ui.patient.register.components.KeepScreenOn
import org.smartregister.fhircore.quest.ui.shared.models.RegisterViewData

@Composable
fun PatientRegisterScreen(
  modifier: Modifier = Modifier,
  screenTitle: String,
  openDrawer: (Boolean) -> Unit,
  navController: NavHostController,
  patientRegisterViewModel: PatientRegisterViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val firstTimeSyncState = patientRegisterViewModel.firstTimeSyncState.collectAsState()
  val firstTimeSync by remember { firstTimeSyncState }
  val searchTextState = patientRegisterViewModel.searchText.collectAsState()
  val searchText by remember { searchTextState }

  val searchedTextState = patientRegisterViewModel.searchedText.collectAsState()
  val searchedText by remember { searchedTextState }
  val patientRegistrationLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult(),
      onResult = {
        if (it.resultCode == Activity.RESULT_OK && it.data != null) {
          val questionnaireResponse =
            it.data!!
              .getStringExtra(QuestionnaireActivity.QUESTIONNAIRE_RESPONSE)
              ?.decodeResourceFromString<QuestionnaireResponse>()
          val patientId = questionnaireResponse?.subject?.extractId()
          if (patientId != null) {
            patientRegisterViewModel.syncBroadcaster.runSync()
            patientRegisterViewModel.onEvent(
              PatientRegisterEvent.OpenProfile(patientId, navController),
            )
          }
        }
      },
    )

  val pagingItems: LazyPagingItems<RegisterViewData.ListItemView> =
    patientRegisterViewModel.pageRegisterListItemData
      .collectAsState()
      .value
      .collectAsLazyPagingItems()

  LaunchedEffect(key1 = searchText.trim()) {
    if (searchText.trim().isNotEmpty()) {
      patientRegisterViewModel.searchPatient(searchText.trim())
    }
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  val coroutineScope = rememberCoroutineScope()

  val snackbarHostState = remember { SnackbarHostState() }

  var showProgressDialog by remember { mutableStateOf(false) }
  var showAttentionDialog by remember { mutableStateOf(false) }
  var onSyncListenerDialog by remember { mutableStateOf(false) }
  var keepScreenOn by remember { mutableStateOf(false) }

  LaunchedEffect(key1 = patientRegisterViewModel.channelFlow) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      patientRegisterViewModel.channelFlow.collect {
        when (it) {
          is EventCallback.InProgress -> {
            showProgressDialog = true
            keepScreenOn = true
          }
          is EventCallback.Finished -> {
            with(patientRegisterViewModel) {
              onEvent(PatientRegisterEvent.SearchRegister(searchText = ""))
              onEvent(PatientRegisterEvent.OpenProfile(it.logicalId, navController))
              showLocalData()
              keepScreenOn = false
            }
          }
          EventCallback.Stated -> {
            coroutineScope.launch {
              snackbarHostState.showSnackbar(message = "Working on it, please wait ...")
            }
          }
          is EventCallback.OnSyncListener -> {
            with(patientRegisterViewModel.getSyncState() == SyncState.SubSequentSync.value) {
              onSyncListenerDialog = this
              keepScreenOn = not()
            }
          }
          EventCallback.ShowAttentionDialog -> {
            showAttentionDialog = true
          }
        }
      }
    }
  }

  val state = patientRegisterViewModel.progressStatusUiState.collectAsState().value
  val status = state.progress ?: Progress(0, 0, "")

  LaunchedEffect(key1 = status) {
    if (status.totalResources == status.currentPosition.plus(1)) {
      showProgressDialog = false
    }
  }

  KeepScreenOn(keepScreenOn)

  if (showProgressDialog) {
    Dialog(onDismissRequest = { showProgressDialog = false }) {
      Card(modifier = Modifier.padding(24.dp)) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
              progress = 1f,
              modifier = Modifier.size(90.dp),
              strokeWidth = 16.dp,
              color = Color.LightGray,
            )

            CircularProgressIndicator(
              progress = status.currentPosition.toFloat().div(status.totalResources.toFloat()),
              modifier = Modifier.size(90.dp),
              strokeWidth = 16.dp,
              strokeCap = StrokeCap.Round,
            )
          }

          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "${status.currentPosition} of ${status.totalResources}",
            style = MaterialTheme.typography.h5,
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
          )
          Text(text = "This takes a while, please wait ...")
        }
      }
    }
  }

  if (onSyncListenerDialog) {
    Dialog(onDismissRequest = { onSyncListenerDialog = false }) {
      Card(modifier = Modifier.padding(24.dp)) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Spacer(modifier = Modifier.size(8.dp))
          Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(62.dp),
            tint = Color(0xFF1E5220),
          )

          Text(
            text = "Sync Completed",
            style = MaterialTheme.typography.h5,
            color = Color.Gray,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
    }
  }

  if (showAttentionDialog) {
    Dialog(
      onDismissRequest = { showAttentionDialog = false },
      properties =
        DialogProperties(
          dismissOnClickOutside = false,
          dismissOnBackPress = false,
        ),
    ) {
      Card(modifier = Modifier.padding(24.dp)) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Spacer(modifier = Modifier.size(8.dp))
          Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            modifier = Modifier.size(62.dp),
            tint = Color(0xFFFFA726),
          )

          Text(
            text =
              "Please stay on this screen as the initial sync is in progress, we will notify you know when it is completed.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
          )

          Button(
            onClick = {
              showAttentionDialog = false
              keepScreenOn = true
              patientRegisterViewModel.initialSync()
            },
          ) {
            Text(text = "Continue")
          }
        }
      }
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      // Top section has toolbar and a results counts view
      TopScreenSection(
        title = screenTitle,
        searchText = searchText,
        onSearchTextChanged = { searchText ->
          patientRegisterViewModel.onEvent(
            PatientRegisterEvent.SearchRegister(searchText = searchText),
          )
        },
      ) {
        openDrawer(true)
      }
    },
    bottomBar = {
      // Bottom section has a pagination footer and button with client registration action
      // Only show when filtering data is not active
      Column {
        if (searchedText.isEmpty() && pagingItems.itemCount > 0) {
          val pageNavigationItems =
            patientRegisterViewModel.pageNavigationItemViewData
              .collectAsState()
              .value
              .collectAsLazyPagingItems()

          RegisterFooter(
            previousButtonClickListener = {
              patientRegisterViewModel.onEvent(PatientRegisterEvent.MoveToPreviousPage)
            },
            nextButtonClickListener = {
              patientRegisterViewModel.onEvent(PatientRegisterEvent.MoveToNextPage)
            },
            pageNavigationPagingItems = pageNavigationItems,
          )
        }

        if (
          searchedText.isEmpty() &&
            (patientRegisterViewModel.isAppFeatureHousehold() ||
              patientRegisterViewModel.isRegisterFormViaSettingExists())
        ) {
          Button(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            onClick = {
              patientRegistrationLauncher.launch(
                patientRegisterViewModel.patientRegisterQuestionnaireIntent(context),
              )
            },
            enabled = !firstTimeSync,
          ) {
            Text(
              text = stringResource(org.smartregister.fhircore.engine.R.string.register_new_client),
              modifier = modifier.padding(8.dp),
            )
          }
        }
      }
    },
  ) { innerPadding ->
    Box(modifier = modifier.padding(innerPadding)) {
      if (firstTimeSync) {
        LoaderDialog(
          modifier = modifier,
          syncProgressStateFlow = patientRegisterViewModel.syncProgressStateFlow,
        )
      }
      // Only show counter during search
      var iModifier = Modifier.padding(top = 0.dp)
      if (searchedText.isNotEmpty()) {
        iModifier = Modifier.padding(top = 32.dp)
        RegisterHeader(resultCount = pagingItems.itemCount)
      }

      val isRefreshing by patientRegisterViewModel.isRefreshing.collectAsState()
      SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing),
        onRefresh = { patientRegisterViewModel.refresh() },
        //        indicator = { _, _ -> }
      ) {
        RegisterList(
          modifier = iModifier,
          pagingItems = pagingItems,
          onRowClick = { patientId: String ->
            pagingItems.itemSnapshotList.items
              .find { it.logicalId == patientId }
              ?.let {
                if (it.isLocal.not()) {
                  patientRegisterViewModel.fetchAndSaveToDb(it.logicalId)
                  return@RegisterList
                }
              }

            patientRegisterViewModel.onEvent(
              PatientRegisterEvent.OpenProfile(patientId, navController),
            )
          },
          progressMessage = patientRegisterViewModel.progressMessage(),
        )
      }
    }
  }
}
