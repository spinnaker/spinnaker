
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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Authorizable;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.User;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Component
@Slf4j
public class FiatPermissionEvaluator implements PermissionEvaluator {

  private final Registry registry;
  private final FiatService fiatService;
  private final FiatClientConfigurationProperties configProps;

  private final Cache<String, UserPermission.View> permissionsCache;

  private final Id getPermissionCounterId;

  @Autowired
  public FiatPermissionEvaluator(Registry registry,
                                 FiatService fiatService,
                                 FiatClientConfigurationProperties configProps) {
    this.registry = registry;
    this.fiatService = fiatService;
    this.configProps = configProps;

    this.permissionsCache = CacheBuilder
        .newBuilder()
        .maximumSize(configProps.getCache().getMaxEntries())
        .expireAfterWrite(configProps.getCache().getExpiresAfterWriteSeconds(), TimeUnit.SECONDS)
        .recordStats()
        .build();

    this.getPermissionCounterId = registry.createId("fiat.getPermission");
  }

  @Override
  public boolean hasPermission(Authentication authentication,
                               Object resource,
                               Object authorization) {
    return false;
  }

  @Override
  public boolean hasPermission(Authentication authentication,
                               Serializable resourceName,
                               String resourceType,
                               Object authorization) {
    if (!configProps.isEnabled()) {
      return true;
    }
    if (resourceName == null || resourceType == null || authorization == null) {
      log.debug("Permission denied due to null argument. resourceName={}, resourceType={}, " +
          "authorization={}", resourceName, resourceType, authorization);
      return false;
    }

    ResourceType r = ResourceType.parse(resourceType);
    Authorization a = null;
    // Service accounts don't have read/write authorizations.
    if (r != ResourceType.SERVICE_ACCOUNT) {
      a = Authorization.valueOf(authorization.toString());
    }

    if (r == ResourceType.APPLICATION && StringUtils.isNotEmpty(resourceName.toString())) {
      resourceName = resourceName.toString();
    }

    UserPermission.View permission = getPermission(getUsername(authentication));
    return permissionContains(permission, resourceName.toString(), r, a);
  }

  private String getUsername(Authentication authentication) {
    String username = "anonymous";
    if (authentication.isAuthenticated() && authentication.getPrincipal() != null) {
      Object principal = authentication.getPrincipal();
      if (principal instanceof User) {
        username = ((User) principal).getUsername();
      } else if (StringUtils.isNotEmpty(principal.toString())) {
        username = principal.toString();
      }
    }
    return username;
  }

  public UserPermission.View getPermission(String username) {
    UserPermission.View view = null;
    if (StringUtils.isEmpty(username)) {
      return null;
    }

    AtomicBoolean cacheHit = new AtomicBoolean(true);
    boolean successfulLookup = true;

    try {
      view = permissionsCache.get(username, () -> {
        cacheHit.set(false);
        return AuthenticatedRequest.propagate(() -> fiatService.getUserPermission(username)).call();
      });
    } catch (ExecutionException | UncheckedExecutionException ee) {
      successfulLookup = false;

      String message = String.format(
          "Cannot get whole user permission for user %s. Cause: %s",
          username,
          ee.getCause().getMessage()
      );
      if (log.isDebugEnabled()) {
        log.debug(message, ee.getCause());
      } else {
        log.info(message);
      }
    }

    registry.counter(
        getPermissionCounterId
            .withTag("cached", cacheHit.get())
            .withTag("success", successfulLookup)
    ).increment();

    return view;
  }

  @SuppressWarnings("unused")
  @Deprecated
  public boolean storeWholePermission() {
    if (!configProps.isEnabled()) {
      return true;
    }

    val authentication = SecurityContextHolder.getContext().getAuthentication();
    val permission = getPermission(getUsername(authentication));
    return permission != null;
  }

  private boolean permissionContains(UserPermission.View permission,
                                     String resourceName,
                                     ResourceType resourceType,
                                     Authorization authorization) {
    if (permission == null) {
      return false;
    }

    Function<Set<? extends Authorizable>, Boolean> containsAuth = resources ->
        resources
            .stream()
            .anyMatch(view -> view.getName().equalsIgnoreCase(resourceName) &&
                view.getAuthorizations().contains(authorization));


    switch (resourceType) {
      case ACCOUNT:
        return containsAuth.apply(permission.getAccounts());
      case APPLICATION:
        return containsAuth.apply(permission.getApplications());
      case SERVICE_ACCOUNT:
        return permission.getServiceAccounts()
            .stream()
            .anyMatch(view -> view.getName().equalsIgnoreCase(resourceName));
      default:
        return false;
    }
  }

  @SuppressWarnings("unused")
  public boolean isAdmin() {
    return true; // TODO(ttomsu): Chosen by fair dice roll. Guaranteed to be random.
  }
}
