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

package org.smartregister.fhircore.quest.ui.localChange

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Badge
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.smartregister.fhircore.engine.data.local.localChange.LocalChangeStateEvent

@Composable
fun LocalChangeScreen(
  state: LocalChangeState,
  event: (LocalChangeEvent) -> Unit,
) {
  val context = LocalContext.current
  var targetValue by remember { mutableIntStateOf(0) }
  val animateState by
    animateIntAsState(
      targetValue = targetValue,
      animationSpec =
        tween(
          easing = FastOutSlowInEasing,
          delayMillis = 1000,
          durationMillis = 5000,
        ),
      label = "",
    )

  LaunchedEffect(key1 = state.localChanges.size) { targetValue = state.localChanges.size }

  LaunchedEffect(key1 = state.localChanges.count { it.status == 2 }) {
    if (
      state.localChanges.count { it.status == 2 } == state.localChanges.size &&
        state.localChanges.isNotEmpty()
    ) {
      event(LocalChangeEvent.Completed)
      Toast.makeText(context, "Syncing", Toast.LENGTH_SHORT).show()
    }
  }

  LaunchedEffect(key1 = state.event) {
    if (state.event is LocalChangeStateEvent.Failed) {
      Toast.makeText(context, "Unable to process request", Toast.LENGTH_SHORT).show()
    }
  }

  Card(
    modifier = Modifier.padding(8.dp).fillMaxWidth().height(IntrinsicSize.Min),
  ) {
    Column(modifier = Modifier.padding(24.dp)) {
      Row(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Staged Resources",
            style = MaterialTheme.typography.h4.copy(color = Color.Gray),
          )

          Row(modifier = Modifier.padding(vertical = 2.dp)) {
            AnimatedVisibility(state.localChanges.any { it.status == 2 }) {
              Badge(
                backgroundColor = Color(0xFF033603),
                modifier = Modifier.clip(RoundedCornerShape(50)),
              ) {
                Text(
                  text = "${state.localChanges.count { it.status == 2 }} SUCCEED",
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(4.dp),
                  color = Color.White,
                  style = MaterialTheme.typography.button.copy(letterSpacing = 0.sp),
                )
              }
            }

            Spacer(modifier = Modifier.width(4.dp))

            AnimatedVisibility(state.localChanges.any { it.status == 3 }) {
              Badge(
                modifier = Modifier.clip(RoundedCornerShape(50)),
              ) {
                Text(
                  text = "${state.localChanges.count { it.status == 3 }} FAILED",
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(4.dp),
                  style = MaterialTheme.typography.button.copy(letterSpacing = 0.sp),
                )
              }
            }
          }
        }

        Text(
          text = "$animateState",
          style = MaterialTheme.typography.h3.copy(color = Color.Black),
        )

        Spacer(modifier = Modifier.width(8.dp))
      }
    }
  }
}
