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

import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.config.AuthorizationConfig;
import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.ResourceType;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationSupport {

  private static final Logger log = LoggerFactory.getLogger(AuthorizationSupport.class);

  private final PermissionEvaluator permissionEvaluator;
  private final ResourceAclResolver resourceAclResolver;
  private final AuthorizationConfig authorizationConfig;

  public AuthorizationSupport(
      PermissionEvaluator permissionEvaluator,
      ResourceAclResolver resourceAclResolver,
      AuthorizationConfig authorizationConfig) {
    this.permissionEvaluator = permissionEvaluator;
    this.resourceAclResolver = resourceAclResolver;
    this.authorizationConfig = authorizationConfig;
  }

  @PostConstruct
  void logRelaxedAuthorization() {
    AuthorizationConfig.RunAsUserRoleCheck runAsUserRoleCheck =
        authorizationConfig.getRunAsUserRoleCheck();
    if (runAsUserRoleCheck.isSkipAll()) {
      log.warn(
          "authorization.run-as-user-role-check.skip-all is true: any pipeline saver with "
              + "APPLICATION:WRITE may use any service account as trigger runAsUser. The "
              + "role-sharing privilege-escalation guard is disabled.");
      return;
    }
    Set<String> skipFor = runAsUserRoleCheck.getSkipFor();
    if (!skipFor.isEmpty()) {
      log.warn(
          "authorization.run-as-user-role-check.skip-for is non-empty: any pipeline saver "
              + "with APPLICATION:WRITE may use these service accounts as trigger runAsUser "
              + "without the default role-sharing check. Bypassed accounts: {}",
          skipFor);
    }
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
    // Owner-local: Front50 owns service-account ACLs, so it resolves the service account's own
    // roles here and presents them as the service account's authorities. (The legacy authorization
    // service used to resolve these server-side by principal name; in the token model the
    // authorities must be populated.)
    List<GrantedAuthority> authorities = new ArrayList<>();
    Permissions serviceAccountAcl =
        resourceAclResolver.resolve(ResourceType.SERVICE_ACCOUNT, runAsUser);
    if (serviceAccountAcl != null) {
      serviceAccountAcl.allGroups().stream()
          .map(SpinnakerAuthorities::forRoleName)
          .forEach(authorities::add);
    }
    Authentication auth = new PreAuthenticatedAuthenticationToken(runAsUser, null, authorities);

    return permissionEvaluator.hasPermission(auth, application, "APPLICATION", "EXECUTE");
  }
}
