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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.SpinnakerAuthorities;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Authorizable;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.telemetry.caffeine.CaffeineStatsCounter;
import com.netflix.spinnaker.security.AccessControlled;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.UserPermissionEvaluator;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

@Component
@Slf4j
public class FiatPermissionEvaluator implements UserPermissionEvaluator {
  private static final ThreadLocal<AuthorizationFailure> authorizationFailure = new ThreadLocal<>();

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

  /** @see ExponentialBackOff */
  static class ExponentialBackoffRetryHandler implements RetryHandler {
    private final FiatClientConfigurationProperties.RetryConfiguration retryConfiguration;

    public ExponentialBackoffRetryHandler(
        FiatClientConfigurationProperties.RetryConfiguration retryConfiguration) {
      this.retryConfiguration = retryConfiguration;
    }

    public <T> T retry(String description, Callable<T> callable) throws Exception {
      ExponentialBackOff backOff =
          new ExponentialBackOff(
              retryConfiguration.getInitialBackoffMillis(),
              retryConfiguration.getRetryMultiplier());
      backOff.setMaxElapsedTime(retryConfiguration.getMaxBackoffMillis());
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
  public FiatPermissionEvaluator(
      Registry registry,
      FiatService fiatService,
      FiatClientConfigurationProperties configProps,
      FiatStatus fiatStatus) {
    this(registry, fiatService, configProps, fiatStatus, buildRetryHandler(configProps));
  }

  private static RetryHandler buildRetryHandler(
      FiatClientConfigurationProperties fiatClientConfigurationProperties) {
    return new ExponentialBackoffRetryHandler(fiatClientConfigurationProperties.getRetry());
  }

  FiatPermissionEvaluator(
      Registry registry,
      FiatService fiatService,
      FiatClientConfigurationProperties configProps,
      FiatStatus fiatStatus,
      RetryHandler retryHandler) {
    this.registry = registry;
    this.fiatService = fiatService;
    this.fiatStatus = fiatStatus;
    this.retryHandler = retryHandler;

    this.permissionsCache =
        Caffeine.newBuilder()
            .maximumSize(configProps.getCache().getMaxEntries())
            .expireAfterWrite(
                configProps.getCache().getExpiresAfterWriteSeconds(), TimeUnit.SECONDS)
            .recordStats(() -> new CaffeineStatsCounter(registry, "fiat.permissionsCache"))
            .build();

    this.getPermissionCounterId = registry.createId("fiat.getPermission");
  }

  @Override
  public boolean hasPermission(
      Authentication authentication, Object resource, Object authorization) {
    if (!fiatStatus.isGrantedAuthoritiesEnabled()) {
      return false;
    }
    if (!fiatStatus.isEnabled()) {
      return true;
    }
    if (authentication == null || resource == null) {
      log.warn(
          "Permission denied because at least one of the required arguments was null. authentication={}, resource={}",
          authentication,
          resource);
      return false;
    }
    if (authentication.getAuthorities().contains(SpinnakerAuthorities.ADMIN_AUTHORITY)) {
      return true;
    }
    if (resource instanceof AccessControlled) {
      return ((AccessControlled) resource).isAuthorized(authentication, authorization);
    }
    return false;
  }

  public boolean canCreate(String resourceType, Object resource) {
    if (!fiatStatus.isEnabled()) {
      return true;
    }

    String username = getUsername(SecurityContextHolder.getContext().getAuthentication());

    try {
      return AuthenticatedRequest.propagate(
              () -> {
                return retryHandler.retry(
                    "determine whether " + username + " can create resource " + resource,
                    () -> {
                      try {
                        Retrofit2SyncCall.execute(
                            fiatService.canCreate(username, resourceType, resource));
                        return true;
                      } catch (SpinnakerHttpException e) {
                        if (e.getResponseCode() == HttpStatus.NOT_FOUND.value()) {
                          return false;
                        }
                        throw e;
                      }
                    });
              })
          .call();
    } catch (Exception e) {
      log.info(e.toString());
      return false;
    }
  }

  /**
   * @param username the username to check
   * @return whether a permission is currently cached for the username
   */
  @SuppressWarnings("unused")
  public boolean hasCachedPermission(String username) {
    if (!fiatStatus.isEnabled()) {
      return true;
    }

    return permissionsCache.getIfPresent(username) != null;
  }

  @Override
  public boolean hasPermission(
      String username, Serializable resourceName, String resourceType, Object authorization) {
    if (!fiatStatus.isEnabled()) {
      return true;
    }
    if (resourceName == null || resourceType == null || authorization == null) {
      log.warn(
          "Permission denied because at least one of the required arguments was null. resourceName={}, resourceType={}, "
              + "authorization={}",
          resourceName,
          resourceType,
          authorization);
      return false;
    }

    ResourceType r = ResourceType.parse(resourceType);
    Authorization a = null;

    // Service accounts don't have read/write authorizations.
    if (!r.equals(ResourceType.SERVICE_ACCOUNT)) {
      a = Authorization.parse(authorization);
    }

    if (a == Authorization.CREATE) {
      throw new IllegalArgumentException(
          "This method should not be called for `CREATE`. Please call the other implementation");
    }

    if (r.equals(ResourceType.APPLICATION) && StringUtils.isNotEmpty(resourceName.toString())) {
      resourceName = resourceName.toString();
    }

    UserPermission.View permission = getPermission(username);
    boolean hasPermission = permissionContains(permission, resourceName.toString(), r, a);

    authorizationFailure.set(
        hasPermission ? null : new AuthorizationFailure(a, r, resourceName.toString()));

    if (permission != null && permission.isLegacyFallback() && hasPermission) {
      // log any access that was granted as part of a legacy fallback.
      if (a == Authorization.READ) {
        // purposely logging at 'debug' as 'READ' will be sufficiently more verbose
        log.debug("Legacy fallback granted {} access (type: {}, resource: {})", a, r, resourceName);
      } else {
        log.warn("Legacy fallback granted {} access (type: {}, resource: {})", a, r, resourceName);
      }
    }

    return hasPermission;
  }

  @Override
  public boolean hasPermission(
      Authentication authentication,
      Serializable resourceName,
      String resourceType,
      Object authorization) {
    if (!fiatStatus.isEnabled()) {
      return true;
    }
    return hasPermission(getUsername(authentication), resourceName, resourceType, authorization);
  }

  /**
   * Invalidates the cached permissions for a user.
   *
   * @param username the username of the user to invalidate from the local cache.
   */
  @SuppressWarnings("unused")
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
      view =
          permissionsCache.get(
              username,
              (loadUserName) -> {
                cacheHit.set(false);
                try {
                  return AuthenticatedRequest.propagate(
                          () -> {
                            try {
                              return retryHandler.retry(
                                  "getUserPermission for " + loadUserName,
                                  () ->
                                      Retrofit2SyncCall.execute(
                                          fiatService.getUserPermission(loadUserName)));
                            } catch (Exception e) {
                              if (!fiatStatus.isLegacyFallbackEnabled()) {
                                throw e;
                              }

                              legacyFallback.set(true);
                              successfulLookup.set(false);
                              exception.set(e);

                              // this fallback permission will be temporarily cached in the
                              // permissions cache
                              return buildFallbackView();
                            }
                          })
                      .call();
                } catch (RuntimeException re) {
                  throw re;
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
    } catch (Exception e) {
      successfulLookup.set(false);
      exception.set(e.getCause() != null ? e.getCause() : e);
    }

    Id id =
        getPermissionCounterId
            .withTag("cached", cacheHit.get())
            .withTag("success", successfulLookup.get());

    if (!successfulLookup.get()) {
      log.error(
          "Cannot get whole user permission for user {}, reason: {} (fallbackAccounts: {})",
          username,
          exception.get().getMessage(),
          getAccountsForView(view));
      id = id.withTag("legacyFallback", legacyFallback.get());
    }

    registry.counter(id).increment();

    if (view != null && view.isLegacyFallback() && view.getAccounts().isEmpty()) {
      // rebuild a potentially stale (could have come from the cache) legacy fallback
      view = buildFallbackView();

      log.debug(
          "Rebuilt legacy fallback user permission for {} (fallbackAccounts: {})",
          username,
          getAccountsForView(view));
    }

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

  public static Optional<AuthorizationFailure> getAuthorizationFailure() {
    return Optional.ofNullable(authorizationFailure.get());
  }

  private String getUsername(Authentication authentication) {
    String username = "anonymous";
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() != null) {
      Object principal = authentication.getPrincipal();
      if (principal instanceof UserDetails) {
        username = ((UserDetails) principal).getUsername();
      } else if (StringUtils.isNotEmpty(principal.toString())) {
        username = principal.toString();
      }
    }
    return username;
  }

  private boolean permissionContains(
      UserPermission.View permission,
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

    Function<Set<? extends Authorizable>, Boolean> containsAuth =
        resources ->
            resources.stream()
                .anyMatch(
                    view -> {
                      Set<Authorization> authorizations =
                          Optional.ofNullable(view.getAuthorizations())
                              .orElse(Collections.emptySet());

                      return view.getName().equalsIgnoreCase(resourceName)
                          && authorizations.contains(authorization);
                    });

    if (resourceType.equals(ResourceType.ACCOUNT)) {
      boolean authorized = containsAuth.apply(permission.getAccounts());

      // Todo(jonsie): Debug transitory access denied issue, remove when not necessary
      if (!authorized) {
        Map<String, Set<Authorization>> accounts =
            permission.getAccounts().stream()
                .collect(Collectors.toMap(Account.View::getName, Account.View::getAuthorizations));

        log.debug(
            "Authorization={} denied to account={} for user permission={}, found={}",
            authorization.toString(),
            resourceName,
            permission.getName(),
            accounts.toString());
      }

      return authorized;
    } else if (resourceType.equals(ResourceType.APPLICATION)) {
      boolean applicationHasPermissions =
          permission.getApplications().stream()
              .anyMatch(a -> a.getName().equalsIgnoreCase(resourceName));

      if (!applicationHasPermissions && permission.isAllowAccessToUnknownApplications()) {
        // allow access to any applications w/o explicit permissions
        return true;
      }
      return permission.isLegacyFallback() || containsAuth.apply(permission.getApplications());
    } else if (resourceType.equals(ResourceType.SERVICE_ACCOUNT)) {
      return permission.getServiceAccounts().stream()
          .anyMatch(view -> view.getName().equalsIgnoreCase(resourceName));
    } else if (resourceType.equals(ResourceType.BUILD_SERVICE)) {
      return permission.isLegacyFallback() || containsAuth.apply(permission.getBuildServices());
    } else if (permission.getExtensionResources() != null
        && permission.getExtensionResources().containsKey(resourceType)) {
      val extensionResources = permission.getExtensionResources().get(resourceType);
      return permission.isLegacyFallback() || containsAuth.apply(extensionResources);
    } else {
      return false;
    }
  }

  private UserPermission.View buildFallbackView() {
    return new UserPermission.View(
            new UserPermission()
                .setId(AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"))
                .setAccounts(
                    Arrays.stream(AuthenticatedRequest.getSpinnakerAccounts().orElse("").split(","))
                        .filter(a -> a != null && !a.isEmpty())
                        .map(a -> new Account().setName(a))
                        .collect(Collectors.toSet())))
        .setLegacyFallback(true)
        .setAllowAccessToUnknownApplications(true);
  }

  private String getAccountsForView(UserPermission.View view) {
    String fallbackAccounts = "''";

    if (view != null && view.getAccounts() != null && !view.getAccounts().isEmpty()) {
      fallbackAccounts =
          view.getAccounts().stream().map(Account.View::getName).collect(Collectors.joining(","));
    }

    return fallbackAccounts;
  }

  /*
   * Used in Front50 Batch APIs
   */
  @SuppressWarnings("unused")
  public boolean isAdmin() {
    return isAdmin(SecurityContextHolder.getContext().getAuthentication());
  }

  public boolean isAdmin(Authentication authentication) {
    if (!fiatStatus.isEnabled()) {
      return true;
    }
    UserPermission.View permission = getPermission(getUsername(authentication));
    return permission != null && permission.isAdmin();
  }

  public static class AuthorizationFailure {
    private final Authorization authorization;
    private final ResourceType resourceType;
    private final String resourceName;

    public AuthorizationFailure(
        Authorization authorization, ResourceType resourceType, String resourceName) {
      this.authorization = authorization;
      this.resourceType = resourceType;
      this.resourceName = resourceName;
    }

    public Authorization getAuthorization() {
      return authorization;
    }

    public boolean hasAuthorization() {
      return authorization != null;
    }

    @Nonnull
    public ResourceType getResourceType() {
      return resourceType;
    }

    @Nonnull
    public String getResourceName() {
      return resourceName;
    }
  }
}
