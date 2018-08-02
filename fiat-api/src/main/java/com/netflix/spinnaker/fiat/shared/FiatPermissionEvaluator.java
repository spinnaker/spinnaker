
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
import com.netflix.spinnaker.fiat.model.resources.Account;
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
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class FiatPermissionEvaluator implements PermissionEvaluator {
  private final Registry registry;
  private final FiatService fiatService;
  private final FiatStatus fiatStatus;

  private final Cache<String, UserPermission.View> permissionsCache;

  private final Id getPermissionCounterId;

  private final RetryHandler retryHandler;


  interface RetryHandler {
    default <T> T retry(String description, Callable<T> callable) throws Exception {
      return callable.call();
    }

    RetryHandler NOOP = new RetryHandler() {};
  }

  /**
   * @see ExponentialBackOff
   */
  static class ExponentialBackoffRetryHandler implements RetryHandler {
    private final long maxBackoff;
    private final long initialBackoff;
    private final double backoffMultiplier;

    public ExponentialBackoffRetryHandler(long maxBackoff, long initialBackoff, double backoffMultiplier) {
      this.maxBackoff = maxBackoff;
      this.initialBackoff = initialBackoff;
      this.backoffMultiplier = backoffMultiplier;
    }

    public <T> T retry(String description, Callable<T> callable) throws Exception {
      ExponentialBackOff backOff = new ExponentialBackOff(initialBackoff, backoffMultiplier);
      backOff.setMaxElapsedTime(maxBackoff);
      BackOffExecution backOffExec = backOff.start();
      while (true) {
        try {
          return callable.call();
        } catch (Throwable e) {
          long waitTime = backOffExec.nextBackOff();
          if (waitTime == BackOffExecution.STOP) {
            throw e;
          }
          log.warn(description + " failed. Retrying in " + waitTime + "ms", e);
          TimeUnit.MILLISECONDS.sleep(waitTime);
        }
      }
    }

  }

  @Autowired
  public FiatPermissionEvaluator(Registry registry,
                                 FiatService fiatService,
                                 FiatClientConfigurationProperties configProps,
                                 FiatStatus fiatStatus) {
    this(registry, fiatService, configProps, fiatStatus, buildRetryHandler(configProps));
  }

  private static RetryHandler buildRetryHandler(FiatClientConfigurationProperties fiatClientConfigurationProperties) {
    FiatClientConfigurationProperties.RetryConfiguration retry = fiatClientConfigurationProperties.getRetry();
    return new ExponentialBackoffRetryHandler(retry.getMaxBackoffMillis(), retry.getInitialBackoffMillis(), retry.getRetryMultiplier());
  }

  FiatPermissionEvaluator(Registry registry,
                          FiatService fiatService,
                          FiatClientConfigurationProperties configProps,
                          FiatStatus fiatStatus,
                          RetryHandler retryHandler) {
    this.registry = registry;
    this.fiatService = fiatService;
    this.fiatStatus = fiatStatus;
    this.retryHandler = retryHandler;

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
    if (!fiatStatus.isEnabled()) {
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

  public void invalidatePermission(String username) {
    permissionsCache.invalidate(username);
  }

  public UserPermission.View getPermission(String username) {
    UserPermission.View view = null;
    if (StringUtils.isEmpty(username)) {
      return null;
    }

    AtomicBoolean cacheHit = new AtomicBoolean(true);
    AtomicBoolean successfulLookup = new AtomicBoolean(true);
    AtomicBoolean legacyFallback = new AtomicBoolean(false);
    AtomicReference<Throwable> exception = new AtomicReference<>();

    try {
      view = permissionsCache.get(username, () -> {
        cacheHit.set(false);
        return AuthenticatedRequest.propagate(() -> {
          try {
            return retryHandler.retry("getUserPermission for " + username, () -> fiatService.getUserPermission(username));
          } catch (Exception e) {
            if (!fiatStatus.isLegacyFallbackEnabled()) {
              throw e;
            }

            legacyFallback.set(true);
            successfulLookup.set(false);
            exception.set(e);

            // this fallback permission will be temporarily cached in the permissions cache
            return new UserPermission.View(
                new UserPermission()
                    .setId(AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"))
                    .setAccounts(
                        Arrays
                            .stream(AuthenticatedRequest.getSpinnakerAccounts().orElse("").split(","))
                            .map(a -> new Account().setName(a))
                            .collect(Collectors.toSet())
                    )
            ).setLegacyFallback(true);
          }
        }).call();
      });
    } catch (ExecutionException | UncheckedExecutionException e) {
      successfulLookup.set(false);
      exception.set(e.getCause() != null ? e.getCause() : e);
    }

    Id id = getPermissionCounterId
        .withTag("cached", cacheHit.get())
        .withTag("success", successfulLookup.get());

    if (!successfulLookup.get()) {
      log.error(
              "Cannot get whole user permission for user {}, reason: {}",
              username,
              exception.get().getMessage(),
              exception
      );
      id = id.withTag("legacyFallback", legacyFallback.get());
    }

    if (legacyFallback.get()) {
      permissionsCache.invalidate(username);
    }

    registry.counter(id).increment();

    return view;
  }

  @SuppressWarnings("unused")
  @Deprecated
  public boolean storeWholePermission() {
    if (!fiatStatus.isEnabled()) {
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

    if (permission.isAdmin()) {
      // grant access regardless of whether an explicit permission to the resource exists
      return true;
    }

    Function<Set<? extends Authorizable>, Boolean> containsAuth = resources ->
        resources
            .stream()
            .anyMatch(view -> {
              Set<Authorization> authorizations = Optional.ofNullable(
                  view.getAuthorizations()
              ).orElse(Collections.emptySet());

              return view.getName().equalsIgnoreCase(resourceName) && authorizations.contains(authorization);
            });

    switch (resourceType) {
      case ACCOUNT:
        return containsAuth.apply(permission.getAccounts());
      case APPLICATION:
        boolean applicationHasPermissions = permission
            .getApplications()
            .stream().anyMatch(a -> a.getName().equalsIgnoreCase(resourceName));

        if (!applicationHasPermissions && permission.isAllowAccessToUnknownApplications()) {
          // allow access to any applications w/o explicit permissions
          return true;
        }

        return permission.isLegacyFallback() || containsAuth.apply(permission.getApplications());
      case SERVICE_ACCOUNT:
        return permission.getServiceAccounts()
            .stream()
            .anyMatch(view -> view.getName().equalsIgnoreCase(resourceName));
      default:
        return false;
    }
  }

  /*
   * Used in Front50 Batch APIs
   */
  @SuppressWarnings("unused")
  public boolean isAdmin() {
    return true; // TODO(ttomsu): Chosen by fair dice roll. Guaranteed to be random.
  }
}
