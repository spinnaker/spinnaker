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

package com.netflix.spinnaker.front50.controllers;

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationSupport {

  private static final Logger log = LoggerFactory.getLogger(AuthorizationSupport.class);

  private final FiatPermissionEvaluator permissionEvaluator;

  public AuthorizationSupport(FiatPermissionEvaluator permissionEvaluator) {
    this.permissionEvaluator = permissionEvaluator;
  }

  public boolean hasRunAsUserPermission(final Pipeline pipeline) {
    List<String> runAsUsers =
        Optional.ofNullable(pipeline.getTriggers())
            .map(
                triggers ->
                    triggers.stream()
                        .map(it -> (String) it.get("runAsUser"))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))
            .orElse(Collections.emptyList());

    if (runAsUsers.isEmpty()) {
      return true;
    }

    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    return runAsUsers.stream()
        .noneMatch(
            runAsUser -> {
              if (!userCanAccessServiceAccount(auth, runAsUser)) {
                log.error(
                    "User {} does not have access to service account {}",
                    Optional.ofNullable(auth).map(Authentication::getPrincipal).orElse("unknown"),
                    runAsUser);
                return true;
              }
              if (!serviceAccountCanAccessApplication(runAsUser, pipeline.getApplication())) {
                log.error(
                    "Service account {} does not have access to application {}",
                    runAsUser,
                    pipeline.getApplication());
                return true;
              }
              return false;
            });
  }

  public boolean userCanAccessServiceAccount(Authentication auth, String runAsUser) {
    return permissionEvaluator.hasPermission(
        auth, runAsUser, "SERVICE_ACCOUNT", "ignored-svcAcct-auth");
  }

  public boolean serviceAccountCanAccessApplication(String runAsUser, String application) {
    Authentication auth =
        new PreAuthenticatedAuthenticationToken(runAsUser, null, new ArrayList<>());

    return permissionEvaluator.hasPermission(auth, application, "APPLICATION", "EXECUTE");
  }
}
