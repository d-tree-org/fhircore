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

package org.smartregister.fhircore.engine.ui.settings.views

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.smartregister.fhircore.engine.R
import org.smartregister.fhircore.engine.configuration.preferences.SyncUploadStrategy

@Composable
fun UploadStrategyOptionButton(modifier: Modifier = Modifier) {
  val context = LocalContext.current

  UserProfileRow(
    icon = Icons.Rounded.BugReport,
    text = stringResource(R.string.upload_strategy),
    clickListener = {
      val singleItems = SyncUploadStrategy.entries.map { it.name }.toTypedArray()
      val checkedItem = 1

      MaterialAlertDialogBuilder(context)
        .setTitle("Select Upload strategy")
        .setNeutralButton(context.getString(R.string.cancel)) { dialog, which ->
          // Respond to neutral button press
        }
        .setPositiveButton(context.getString(R.string.ok)) { dialog, which ->
          // Respond to positive button press
        }
        // Single-choice items (initialized with checked item)
        .setSingleChoiceItems(singleItems, checkedItem) { dialog, which ->
          // Respond to item chosen
        }
        .show()
    },
    modifier = modifier,
  )
}
