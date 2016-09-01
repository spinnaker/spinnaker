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

package com.netflix.spinnaker.fiat.shared;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Authorizable;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

import java.io.Serializable;
import java.util.Set;
import java.util.function.Function;

@Component
@Slf4j
public class FiatPermissionEvaluator implements PermissionEvaluator {

  @Autowired
  @Setter
  private FiatService fiatService;

  @Value("${services.fiat.enabled:false}")
  @Setter
  private String fiatEnabled;

  @Override
  public boolean hasPermission(Authentication authentication, Object resource, Object authorization) {
    return false;
  }

  @Override
  public boolean hasPermission(Authentication authentication, Serializable resourceName, String resourceType, Object authorization) {
    if (!Boolean.valueOf(fiatEnabled)) {
      return true;
    }

    String username = getUsername(authentication);
    Resource r = Resource.parse(resourceType);
    Authorization a = Authorization.valueOf(authorization.toString());

    return isWholePermissionStored(authentication) ?
        permissionContains(authentication, resourceName.toString(), r, a) :
        isAuthorized(username, r, resourceName.toString(), a);
  }

  private String getUsername(Authentication authentication) {
    String username = "anonymous";
    if (authentication instanceof PreAuthenticatedAuthenticationToken) {
      PreAuthenticatedAuthenticationToken authToken = (PreAuthenticatedAuthenticationToken) authentication;
      if (authToken.isAuthenticated()) {
        username = authToken.getPrincipal().toString();
      }
    }
    return username;
  }

  private boolean isAuthorized(String username, Resource resource, String resourceName, Authorization a) {
    try {
      fiatService.hasAuthorization(username, resource.toString(), resourceName, a.toString());
    } catch (RetrofitError re) {
      String message = String.format("Fiat authorization failed for user '%s' '%s'-ing '%s' " +
                                         "resource named '%s'. Cause: %s", username, a, resource, resourceName, re.getMessage());
      log.debug(message);
      log.trace(message, re);
      return false;
    }
    return true;
  }

  @SuppressWarnings("unused")
  public boolean storeWholePermission() {
    if (!Boolean.valueOf(fiatEnabled)) {
      return true;
    }

    String username = getUsername(SecurityContextHolder.getContext().getAuthentication());

    UserPermission.View view;
    try {
      view = fiatService.getUserPermission(username);
    } catch (RetrofitError re) {
      String message = String.format("Cannot get whole user permission for user %s", username);
      log.debug(message);
      log.trace(message, re);
      return false;
    }

    PreAuthenticatedAuthenticationToken auth = new PreAuthenticatedAuthenticationToken(username, null, null);
    auth.setDetails(view);

    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(auth);
    SecurityContextHolder.setContext(ctx);

    return true;
  }

  private boolean isWholePermissionStored(Authentication authentication) {
    return authentication.getDetails() != null &&
        authentication.getDetails() instanceof UserPermission.View;
  }

  private boolean permissionContains(Authentication authentication,
                                     String resourceName,
                                     Resource resource,
                                     Authorization authorization) {
    UserPermission.View permission = (UserPermission.View) authentication.getDetails();

    Function<Set<? extends Authorizable>, Boolean> containsAuth = resources ->
        resources
            .stream()
            .anyMatch(view -> view.getName().equalsIgnoreCase(resourceName) &&
                view.getAuthorizations().contains(authorization));


    switch (resource) {
      case ACCOUNT:
        return containsAuth.apply(permission.getAccounts());
      case APPLICATION:
        return containsAuth.apply(permission.getApplications());
      default:
        return false;
    }
  }

  @SuppressWarnings("unused")
  public boolean isAdmin() {
    return true; // TODO(ttomsu): Chosen by fair dice roll. Guaranteed to be random.
  }
}
