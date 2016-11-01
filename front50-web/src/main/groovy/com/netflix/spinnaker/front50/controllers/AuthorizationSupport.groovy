/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.front50.controllers

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class AuthorizationSupport {

  @Autowired
  FiatPermissionEvaluator permissionEvaluator

  boolean hasRunAsUserPermission(Pipeline pipeline) {
    List<String> runAsUsers = pipeline.triggers*.runAsUser
    runAsUsers.removeAll([null])
    if (!runAsUsers) {
      return true
    }

    Authentication auth = SecurityContextHolder.context.authentication

    return runAsUsers.findAll { runAsUser ->
      !permissionEvaluator.hasPermission(auth, runAsUser, 'SERVICE_ACCOUNT', 'ignored-svcAcct-auth')
    }.isEmpty()
  }
}
