/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoSuchEntityException
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.APPLICATION
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.RESOURCE
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.SERVICE_ACCOUNT
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Support for authorization of REST API calls.
 *
 * @see https://github.com/spinnaker/keel/blob/master/docs/authorization.md
 */
@Component
class AuthorizationSupport(
  private val permissionEvaluator: FiatPermissionEvaluator,
  private val repository: KeelRepository,
  private val dynamicConfigService: DynamicConfigService
) {
  val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  enum class Action {
    READ, WRITE;
    override fun toString() = name.toLowerCase()
  }

  enum class TargetEntity {
    APPLICATION, DELIVERY_CONFIG, RESOURCE, SERVICE_ACCOUNT;
    override fun toString() = name.toLowerCase()
  }

  private fun enabled() = dynamicConfigService.isEnabled("keel.authorization", true)

  /**
   * Returns true if the caller has the specified permission (action) to access the application associated with the
   * specified target object.
   */
  fun hasApplicationPermission(action: String, target: String, identifier: String) =
    passes { checkApplicationPermission(Action.valueOf(action), TargetEntity.valueOf(target), identifier) }

  /**
   * Returns true if  the caller has access to the specified service account.
   */
  fun hasServiceAccountAccess(target: String, identifier: String) =
    passes { checkServiceAccountAccess(TargetEntity.valueOf(target), identifier) }

  /**
   * Returns true if  the caller has access to the specified service account.
   */
  fun hasServiceAccountAccess(serviceAccount: String) =
    passes { checkServiceAccountAccess(SERVICE_ACCOUNT, serviceAccount) }

  /**
   * Returns true if the caller has the specified permission (action) to access the cloud account associated with the
   * specified target object.
   */
  fun hasCloudAccountPermission(action: String, target: String, identifier: String) =
    passes { checkCloudAccountPermission(Action.valueOf(action), TargetEntity.valueOf(target), identifier) }

  /**
   * Verifies that the caller has the specified permission (action) to access the application associated with the
   * specified target object.
   *
   * @throws AccessDeniedException if caller does not have the required permission.
   */
  fun checkApplicationPermission(action: Action, target: TargetEntity, identifier: String) {
    if (!enabled()) return

    withAuthentication(target, identifier) { auth ->
      val application = when (target) {
        RESOURCE -> repository.getResource(identifier).application
        APPLICATION -> identifier
        DELIVERY_CONFIG -> repository.getDeliveryConfig(identifier).application
        else -> throw InvalidRequestException("Invalid target type ${target.name} for application permission check")
      }
      permissionEvaluator.hasPermission(auth, application, "APPLICATION", action.name)
        .also { allowed ->
          log.debug("[ACCESS {}] User {}: {} access to application {}.",
            allowed.toAuthorization(), auth.principal, action.name, application)

          if (!allowed) {
            throw AccessDeniedException("User ${auth.principal} does not have access to application $application")
          }
        }
    }
  }

  /**
   * Verifies that the caller has access to the specified service account.
   *
   * @throws AccessDeniedException if caller does not have the required permission.
   */
  fun checkServiceAccountAccess(target: TargetEntity, identifier: String) {
    if (!enabled()) return

    withAuthentication(target, identifier) { auth ->
      val serviceAccount = when (target) {
        SERVICE_ACCOUNT -> identifier
        RESOURCE -> repository.getResource(identifier).serviceAccount
        APPLICATION -> repository.getDeliveryConfigForApplication(identifier).serviceAccount
        DELIVERY_CONFIG -> repository.getDeliveryConfig(identifier).serviceAccount
      }
      permissionEvaluator.hasPermission(auth, serviceAccount, "SERVICE_ACCOUNT", "ignored")
        .also { allowed ->
          log.debug("[ACCESS {}] User {}: access to service account {}.",
            allowed.toAuthorization(), auth.principal, serviceAccount)

          if (!allowed) {
            throw AccessDeniedException("User ${auth.principal} does not have access to service account $serviceAccount")
          }
        }
    }
  }

  /**
   * Verifies that the caller has the specified permission to all applicable resources (i.e. resources whose specs
   * are [Locatable]) identified by the target type and identifier, as follows:
   *   - If target is RESOURCE, check the resource itself
   *   - If target is DELIVERY_CONFIG, check all the resources in all the environments of the delivery config
   *   - If target is APPLICATION, do the same as for DELIVERY_CONFIG
   *
   * @throws AccessDeniedException if caller does not have the required permission.
   */
  fun checkCloudAccountPermission(action: Action, target: TargetEntity, identifier: String) {
    if (!enabled()) return

    withAuthentication(target, identifier) { auth ->
      val locatableResources = when (target) {
        RESOURCE -> listOf(repository.getResource(identifier))
        APPLICATION -> repository.getDeliveryConfigForApplication(identifier).resources
        DELIVERY_CONFIG -> repository.getDeliveryConfig(identifier).resources
        else -> throw InvalidRequestException("Invalid target type ${target.name} for cloud account permission check")
      }.filter { it.spec is Locatable<*> }

      locatableResources.forEach {
        val account = (it.spec as Locatable<*>).locations.account
        permissionEvaluator.hasPermission(auth, account, "ACCOUNT", action.name)
          .also { allowed ->
            log.debug("[ACCESS {}] User {}: {} access to cloud account {}.",
              allowed.toAuthorization(), auth.principal, action.name, account)

            if (!allowed) {
              throw AccessDeniedException("User ${auth.principal} does not have access to cloud account $account")
            }
          }
      }
    }
  }

  private fun withAuthentication(target: TargetEntity, identifier: String, block: (Authentication) -> Unit) {
    try {
      val auth = SecurityContextHolder.getContext().authentication
      block(auth)
    } catch (e: NoSuchEntityException) {
      // If entity doesn't exist return true so a 404 is returned from the controller.
      log.debug("${target.name} $identifier not found. Allowing request to return 404.")
    }
  }

  private fun passes(authorizationCheck: () -> Unit) =
    try {
      authorizationCheck()
      true
    } catch (e: AccessDeniedException) {
      false
    }

  private fun Boolean.toAuthorization() = if (this) "ALLOWED" else "DENIED"
}
