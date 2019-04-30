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
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component

@Component
@Slf4j
class AuthorizationSupport {

  @Autowired
  FiatPermissionEvaluator permissionEvaluator

  boolean hasRunAsUserPermission(Pipeline pipeline) {
    List<String> runAsUsers = pipeline.triggers?.findResults { it.runAsUser }
    if (!runAsUsers) {
      return true
    }

    Authentication auth = SecurityContextHolder.context.authentication

    return runAsUsers.findAll { runAsUser ->
      if (!userCanAccessServiceAccount(auth, runAsUser)) {
        log.info("User ${auth?.principal} does not have access to service account $runAsUser")
        return true
      }
      if (!serviceAccountCanAccessApplication(runAsUser, pipeline.application as String)) {
        log.info("Service account ${runAsUser} does not have access to application ${pipeline.application}")
        return true
      }
      return false
    }.isEmpty()
  }

  boolean userCanAccessServiceAccount(Authentication auth, String runAsUser) {
    return permissionEvaluator.hasPermission(auth,
                                             runAsUser,
                                             'SERVICE_ACCOUNT',
                                             'ignored-svcAcct-auth')
  }

  boolean serviceAccountCanAccessApplication(String runAsUser, String application) {
    Authentication auth = new PreAuthenticatedAuthenticationToken(runAsUser,
                                                                  null,
                                                                  new ArrayList<>());

    return permissionEvaluator.hasPermission(auth,
                                             application,
                                             'APPLICATION',
                                             'EXECUTE')
  }
}
