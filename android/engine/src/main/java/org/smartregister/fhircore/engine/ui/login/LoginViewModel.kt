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

package org.smartregister.fhircore.engine.ui.login

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.FhirEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import io.jsonwebtoken.Jwts
import java.net.UnknownHostException
import javax.inject.Inject
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.StructureMap
import org.jetbrains.annotations.TestOnly
import org.smartregister.fhircore.engine.auth.AccountAuthenticator
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.engine.configuration.app.AppConfigService
import org.smartregister.fhircore.engine.configuration.app.ApplicationConfiguration
import org.smartregister.fhircore.engine.data.local.DefaultRepository
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceDataSource
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceService
import org.smartregister.fhircore.engine.data.remote.model.response.OAuthResponse
import org.smartregister.fhircore.engine.data.remote.model.response.UserClaimInfo
import org.smartregister.fhircore.engine.data.remote.shared.TokenAuthenticator
import org.smartregister.fhircore.engine.domain.model.LocationHierarchy
import org.smartregister.fhircore.engine.ui.questionnaire.ContentCache
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.SecureSharedPreference
import org.smartregister.fhircore.engine.util.SharedPreferenceKey
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import org.smartregister.fhircore.engine.util.extension.extractLogicalIdUuid
import org.smartregister.fhircore.engine.util.extension.getActivity
import org.smartregister.fhircore.engine.util.extension.isDeviceOnline
import org.smartregister.fhircore.engine.util.extension.loadResource
import org.smartregister.fhircore.engine.util.extension.practitionerEndpointUrl
import org.smartregister.fhircore.engine.util.extension.referenceValue
import org.smartregister.fhircore.engine.util.extension.valueToString
import org.smartregister.model.practitioner.PractitionerDetails
import retrofit2.HttpException
import timber.log.Timber

@HiltViewModel
class LoginViewModel
@Inject
constructor(
  val accountAuthenticator: AccountAuthenticator,
  val sharedPreferences: SharedPreferencesHelper,
  val secureSharedPreference: SecureSharedPreference,
  val dispatcherProvider: DispatcherProvider,
  val fhirResourceDataSource: FhirResourceDataSource,
  val defaultRepository: DefaultRepository,
  val tokenAuthenticator: TokenAuthenticator,
  val fhirResourceService: FhirResourceService,
  val configurationRegistry: ConfigurationRegistry,
  private val appConfigs: AppConfigService,
  private val fhirEngine: FhirEngine,
) : ViewModel() {

  private val _launchDialPad: MutableLiveData<String?> = MutableLiveData(null)
  val launchDialPad
    get() = _launchDialPad

  private val _navigateToHome = MutableLiveData(false)
  val navigateToHome: LiveData<Boolean>
    get() = _navigateToHome

  private val _username = MutableLiveData<String>()
  val username: LiveData<String>
    get() = _username

  private val _password = MutableLiveData<String>()
  val password: LiveData<String>
    get() = _password

  private val _loginErrorState = MutableLiveData<LoginErrorState?>()
  val loginErrorState: LiveData<LoginErrorState?>
    get() = _loginErrorState

  private val _showProgressBar = MutableLiveData(false)
  val showProgressBar
    get() = _showProgressBar

  private val _loadingConfig = MutableLiveData(true)
  val loadingConfig: LiveData<Boolean>
    get() = _loadingConfig

  private val _applicationConfiguration = MutableLiveData<ApplicationConfiguration>()
  val applicationConfiguration: LiveData<ApplicationConfiguration>
    get() = _applicationConfiguration

  init {
    _applicationConfiguration.value = configurationRegistry.getAppConfigs()
  }

  private suspend fun fetchAccessToken(
    username: String,
    password: CharArray,
  ): Result<OAuthResponse> =
    tokenAuthenticator.fetchAccessToken(username, password).onFailure {
      _showProgressBar.postValue(false)
      var errorState = LoginErrorState.ERROR_FETCHING_USER

      if (it is HttpException) {
        when (it.code()) {
          401 -> errorState = LoginErrorState.INVALID_CREDENTIALS
        }
      } else if (it is UnknownHostException) {
        errorState = LoginErrorState.UNKNOWN_HOST
      }

      _loginErrorState.postValue(errorState)
      Timber.e(it)
    }

  private suspend fun fetchPractitioner(keycloakUuid: String?): Result<Bundle> {
    val endpointResult =
      keycloakUuid
        ?.takeIf { it.isNotBlank() }
        ?.practitionerEndpointUrl()
        ?.runCatching { fhirResourceService.getResource(url = this) }
        ?: Result.failure(NullPointerException("Keycloak user is null. Failed to fetch user."))
    endpointResult.onFailure {
      _showProgressBar.postValue(false)
      Timber.e(it)
      Timber.e(endpointResult.getOrNull().valueToString())
      _loginErrorState.postValue(LoginErrorState.ERROR_FETCHING_USER)
    }
    return endpointResult
  }

  fun onUsernameUpdated(username: String) {
    _loginErrorState.postValue(null)
    _username.value = username
  }

  fun onPasswordUpdated(password: String) {
    _loginErrorState.postValue(null)
    _password.value = password
  }

  fun login(context: Context, scope: CoroutineScope = viewModelScope) {
    scope.launch(dispatcherProvider.io()) {
      login(offline = !context.getActivity()!!.isDeviceOnline())
    }
  }

  private suspend fun login(offline: Boolean) {
    try {
      val usernameValue = _username.value
      val passwordValue = _password.value
      if (usernameValue.isNullOrBlank() || passwordValue.isNullOrBlank()) return

      _loginErrorState.postValue(null)
      _showProgressBar.postValue(true)

      val trimmedUsername = _username.value!!.trim()
      val passwordAsCharArray = _password.value!!.toCharArray()
      if (offline) {
        warmCache()

        verifyCredentials(trimmedUsername, passwordAsCharArray)
        return
      }

      val practitionerID =
        sharedPreferences.read(key = SharedPreferenceKey.PRACTITIONER_ID.name, defaultValue = null)
      val sessionActiveExists = tokenAuthenticator.sessionActive() && practitionerID != null
      val existingCredentials = secureSharedPreference.retrieveCredentials()
      val multiUserLoginAttempted =
        existingCredentials?.username?.equals(trimmedUsername, true) == false

      warmCache()

      when {
        multiUserLoginAttempted -> {
          _showProgressBar.postValue(false)
          _loginErrorState.postValue(LoginErrorState.MULTI_USER_LOGIN_ATTEMPT)
        }
        sessionActiveExists -> verifyCredentials(trimmedUsername, passwordAsCharArray)
        else -> {
          val accessTokenResult = fetchAccessToken(trimmedUsername, passwordAsCharArray)
          if (accessTokenResult.isFailure) return

          if (accessTokenResult.getOrNull() == null) return

          val jwtParser = Jwts.parser()
          val jwt = accessTokenResult.getOrNull()!!.accessToken!!.substringBeforeLast('.').plus(".")
          val subClaim = jwtParser.parseClaimsJwt(jwt)
          val userInfo = UserClaimInfo.parseFromClaims(subClaim.body)
          val practitionerDetailsResult = fetchPractitioner(userInfo.keycloakUuid)
          if (practitionerDetailsResult.isFailure) return

          if (savePractitionerDetails(practitionerDetailsResult.getOrDefault(Bundle()), userInfo)) {
            updateNavigateHome(true)
          } else {
            _loginErrorState.postValue(LoginErrorState.ACCOUNT_NOT_CONFIGURED_PROPERLY)
          }

          _showProgressBar.postValue(false)
        }
      }
    } catch (ex: Exception) {
      Timber.e(ex)
      if (ex is PractitionerNotFoundException) {
        _loginErrorState.postValue(LoginErrorState.INVALID_CREDENTIALS)
      } else {
        _loginErrorState.postValue(LoginErrorState.ERROR_FETCHING_USER)
      }
      _showProgressBar.postValue(false)
    }
  }

  private suspend fun warmCache() {
    val timeInMillis = measureTimeMillis {
      val registrationResourceId = "patient-demographic-registration"

      val registrationQuestionnaire =
        fhirEngine.loadResource<Questionnaire>(registrationResourceId)?.apply {
          this.url = this.url ?: this.referenceValue()
        }

      val registrationQuestionnaireStructureMap =
        fhirEngine.loadResource<StructureMap>(registrationResourceId)?.apply {
          this.url = this.url ?: this.referenceValue()
        }

      registrationQuestionnaire?.let { ContentCache.saveResource(it) }

      registrationQuestionnaireStructureMap?.let { ContentCache.saveResource(it) }

      Timber.d(
        "Cached Questionnaire ${registrationQuestionnaire?.idPart} and url ${registrationQuestionnaire?.url}",
      )
    }

    Timber.d("Cache reset in $timeInMillis ms")
  }

  private fun verifyCredentials(username: String, password: CharArray) {
    if (accountAuthenticator.validateLoginCredentials(username, password)) {
      _showProgressBar.postValue(false)
      updateNavigateHome(true)
    } else {
      _showProgressBar.postValue(false)
      _loginErrorState.postValue(LoginErrorState.INVALID_CREDENTIALS)
    }
  }

  fun updateNavigateHome(navigateHome: Boolean = true) {
    _navigateToHome.postValue(navigateHome)
  }

  fun forgotPassword() {
    applicationConfiguration.value?.let { config ->
      _launchDialPad.value = "tel:${config.supportPhoneNumber}"
    }
  }

  @TestOnly
  fun navigateToHome(navigateHome: Boolean = true) {
    _navigateToHome.value = navigateHome
    _navigateToHome.postValue(navigateHome)
  }

  suspend fun savePractitionerDetails(bundle: Bundle, userClaimInfo: UserClaimInfo): Boolean {
    if (bundle.entry.isNullOrEmpty()) return false
    val practitionerDetails = bundle.entry.first().resource as PractitionerDetails

    if (
      practitionerDetails.id ==
        "${appConfigs.getBaseFhirUrl()}practitioner-details/Practitioner Not Found"
    ) {
      throw PractitionerNotFoundException()
    }

    val careTeams = practitionerDetails.fhirPractitionerDetails?.careTeams ?: listOf()
    val organizations = practitionerDetails.fhirPractitionerDetails?.organizations ?: listOf()
    val locations = practitionerDetails.fhirPractitionerDetails?.locations ?: listOf()
    val locationHierarchies =
      practitionerDetails.fhirPractitionerDetails?.locationHierarchyList ?: listOf()
    val groups = practitionerDetails.fhirPractitionerDetails?.groups ?: emptyList()
    val practitionerRoles =
      practitionerDetails.fhirPractitionerDetails?.practitionerRoles ?: emptyList()
    val practitioners = practitionerDetails.fhirPractitionerDetails?.practitioners ?: emptyList()

    if (organizations.isEmpty() || locations.isEmpty() || careTeams.isEmpty()) return false

    val remoteResources =
      careTeams.toTypedArray<Resource>() +
        organizations.toTypedArray() +
        locations.toTypedArray() +
        groups.toTypedArray() +
        practitionerRoles.toTypedArray() +
        practitioners.toTypedArray()
    defaultRepository.saveLocalOnly(*remoteResources)

    with(sharedPreferences) {
      val organisationIds = organizations.map { it.id.extractLogicalIdUuid() }
      val practitionerId =
        practitionerDetails.fhirPractitionerDetails
          ?.practitioners
          ?.firstOrNull()
          ?.id
          ?.extractLogicalIdUuid()
      write(ResourceType.CareTeam.name, careTeams.map { it.id.extractLogicalIdUuid() })
      write(ResourceType.Organization.name, organisationIds)
      write(ResourceType.Location.name, locations.map { it.id.extractLogicalIdUuid() })
      write(
        key = SharedPreferenceKey.PRACTITIONER_ID.name,
        value = practitionerId,
      )
      write(
        key = SharedPreferenceKey.PRACTITIONER_LOCATION_HIERARCHIES.name,
        value = locationHierarchies.map { LocationHierarchy.fromLocationHierarchy(it) },
      )
      write(SharedPreferenceKey.PRACTITIONER_DETAILS.name, practitionerDetails)
      write(
        SharedPreferenceKey.USER_CLAIM_INFO.name,
        userClaimInfo.copy(
          organization = organisationIds.firstOrNull() ?: userClaimInfo.organization,
          practitionerId = practitionerId,
        ),
      )
    }
    return true
  }

  fun loadLastLoggedInUsername() {
    _username.postValue(accountAuthenticator.retrieveLastLoggedInUsername() ?: "")
  }

  fun fetchLoginConfigs() {
    _loadingConfig.value = false
  }

  companion object {
    const val IDENTIFIER = "identifier"
  }
}
