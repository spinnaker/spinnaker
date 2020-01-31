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
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.persistence.NoSuchResourceException
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class AuthorizationSupport(
  private val permissionEvaluator: FiatPermissionEvaluator,
  private val resourceRepository: ResourceRepository
) {
  val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  fun userCanModifyResource(name: String): Boolean =
    try {
      val resource = resourceRepository.get(name)
      userCanModifySpec(resource.serviceAccount, resource.id)
    } catch (e: NoSuchResourceException) {
      // If resource doesn't exist return true so a 404 is propagated from the controller.
      true
    }

  fun userCanModifySpec(serviceAccount: String, specOrName: Any): Boolean {
    val auth = SecurityContextHolder.getContext().authentication
    return userCanAccessServiceAccount(auth, serviceAccount, specOrName)
  }

  fun userCanAccessServiceAccount(auth: Authentication, serviceAccount: String, specOrName: Any): Boolean {
    val hasPermission = permissionEvaluator.hasPermission(auth, serviceAccount, "SERVICE_ACCOUNT", "ignored-svcAcct-auth")
    log.debug(
      "[AUTH] {} is trying to access service account {}. They{} have permission. Resource: {}",
      auth.principal,
      serviceAccount,
      if (hasPermission) "" else " DO NOT",
      specOrName
    )
    return hasPermission
  }
}
