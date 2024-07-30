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

package org.smartregister.fhircore.engine.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.smartregister.fhircore.engine.data.local.localChange.repository.LocalChangeRepo
import org.smartregister.fhircore.engine.data.local.localChange.repository.LocalChangeRepoImpl
import org.smartregister.fhircore.engine.data.local.syncAttempt.repository.SyncAttemptTrackerRepo
import org.smartregister.fhircore.engine.data.local.syncAttempt.repository.SyncAttemptTrackerRepoImpl

@InstallIn(SingletonComponent::class)
@Module
abstract class LocalRepositoryModule {

  @Binds
  abstract fun bindSyncAttemptTrackerRepo(param: SyncAttemptTrackerRepoImpl): SyncAttemptTrackerRepo

  @Binds abstract fun bindLocalChangeRepo(param: LocalChangeRepoImpl): LocalChangeRepo
}
