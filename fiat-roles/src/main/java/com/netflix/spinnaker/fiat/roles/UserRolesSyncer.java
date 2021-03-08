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

package com.netflix.spinnaker.fiat.roles;

import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.config.ResourceProvidersHealthIndicator;
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.permissions.PermissionResolutionException;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.PermissionsResolver;
import com.netflix.spinnaker.fiat.providers.ProviderException;
import com.netflix.spinnaker.fiat.providers.ResourceProvider;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.lock.LockManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Component
@ConditionalOnExpression("${fiat.write-mode.enabled:true}")
public class UserRolesSyncer {
  private final DiscoveryStatusListener discoveryStatusListener;

  private final LockManager lockManager;
  private final PermissionsRepository permissionsRepository;
  private final PermissionsResolver permissionsResolver;
  private final ResourceProvider<ServiceAccount> serviceAccountProvider;
  private final ResourceProvidersHealthIndicator healthIndicator;

  private final long retryIntervalMs;
  private final long syncDelayMs;
  private final long syncFailureDelayMs;
  private final long syncDelayTimeoutMs;
  private final String lockName;

  private final Registry registry;
  private final Gauge userRolesSyncCount;

  @Autowired
  public UserRolesSyncer(
      DiscoveryStatusListener discoveryStatusListener,
      Registry registry,
      LockManager lockManager,
      PermissionsRepository permissionsRepository,
      PermissionsResolver permissionsResolver,
      ResourceProvider<ServiceAccount> serviceAccountProvider,
      ResourceProvidersHealthIndicator healthIndicator,
      @Value("${fiat.write-mode.retry-interval-ms:10000}") long retryIntervalMs,
      @Value("${fiat.write-mode.sync-delay-ms:600000}") long syncDelayMs,
      @Value("${fiat.write-mode.sync-failure-delay-ms:600000}") long syncFailureDelayMs,
      @Value("${fiat.write-mode.sync-delay-timeout-ms:30000}") long syncDelayTimeoutMs,
      @Value("${fiat.write-mode.lock-name:}") String lockName) {
    this.discoveryStatusListener = discoveryStatusListener;

    this.lockManager = lockManager;
    this.permissionsRepository = permissionsRepository;
    this.permissionsResolver = permissionsResolver;
    this.serviceAccountProvider = serviceAccountProvider;
    this.healthIndicator = healthIndicator;

    this.retryIntervalMs = retryIntervalMs;
    this.syncDelayMs = syncDelayMs;
    this.syncFailureDelayMs = syncFailureDelayMs;
    this.syncDelayTimeoutMs = syncDelayTimeoutMs;
    this.lockName = lockName;

    this.registry = registry;
    this.userRolesSyncCount = registry.gauge(metricName("syncCount"));
  }

  @Scheduled(fixedDelay = 30000L)
  public void schedule() {
    if (syncDelayMs < 0 || !discoveryStatusListener.isEnabled()) {
      log.warn(
          "User roles syncing is disabled (syncDelayMs: {}, isEnabled: {})",
          syncDelayMs,
          discoveryStatusListener.isEnabled());
      return;
    }

    LockManager.LockOptions lockOptions =
        new LockManager.LockOptions()
            .withLockName(
                (lockName != null && !lockName.isEmpty())
                    ? lockName.toLowerCase()
                    : "Fiat.UserRolesSyncer".toLowerCase())
            .withMaximumLockDuration(Duration.ofMillis(syncDelayMs + syncDelayTimeoutMs))
            .withSuccessInterval(Duration.ofMillis(syncDelayMs))
            .withFailureInterval(Duration.ofMillis(syncFailureDelayMs));

    lockManager.acquireLock(
        lockOptions,
        () -> {
          try {
            timeIt("syncTime", () -> userRolesSyncCount.set(this.syncAndReturn(new ArrayList<>())));
          } catch (Exception e) {
            log.error("User roles synchronization failed", e);
            userRolesSyncCount.set(-1);
          }
        });
  }

  public long syncAndReturn(List<String> roles) {
    FixedBackOff backoff = new FixedBackOff();
    backoff.setInterval(retryIntervalMs);
    backoff.setMaxAttempts(Math.floorDiv(syncDelayTimeoutMs, retryIntervalMs) + 1);
    BackOffExecution backOffExec = backoff.start();

    // after this point the execution will get rescheduled
    final long timeout = System.currentTimeMillis() + syncDelayTimeoutMs;

    if (!isServerHealthy()) {
      log.warn(
          "Server is currently UNHEALTHY. User permission role synchronization and "
              + "resolution may not complete until this server becomes healthy again.");
    }

    // Ensure we're going to reload app and service account definitions
    permissionsResolver.clearCache();

    while (true) {
      try {
        Map<String, UserPermission> combo = new HashMap<>();
        // force a refresh of the unrestricted user in case the backing repository is empty:
        combo.put(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME, new UserPermission());
        Map<String, UserPermission> temp;
        if (!(temp = getUserPermissions(roles)).isEmpty()) {
          combo.putAll(temp);
        }
        if (!(temp = getServiceAccountsAsMap(roles)).isEmpty()) {
          combo.putAll(temp);
        }

        return updateUserPermissions(combo);
      } catch (ProviderException | PermissionResolutionException ex) {
        registry
            .counter(metricName("syncFailure"), "cause", ex.getClass().getSimpleName())
            .increment();
        Status status = healthIndicator.health().getStatus();
        long waitTime = backOffExec.nextBackOff();
        if (waitTime == BackOffExecution.STOP || System.currentTimeMillis() > timeout) {
          String cause = (waitTime == BackOffExecution.STOP) ? "backoff-exhausted" : "timeout";
          registry.counter("syncAborted", "cause", cause).increment();
          log.error("Unable to resolve service account permissions.", ex);
          return 0;
        }
        String message =
            new StringBuilder("User permission sync failed. ")
                .append("Server status is ")
                .append(status)
                .append(". Trying again in ")
                .append(waitTime)
                .append(" ms. Cause:")
                .append(ex.getMessage())
                .toString();
        if (log.isDebugEnabled()) {
          log.debug(message, ex);
        } else {
          log.warn(message);
        }

        try {
          Thread.sleep(waitTime);
        } catch (InterruptedException ignored) {
        }
      } finally {
        isServerHealthy();
      }
    }
  }

  private boolean isServerHealthy() {
    return healthIndicator.health().getStatus() == Status.UP;
  }

  private Map<String, UserPermission> getServiceAccountsAsMap(List<String> roles) {
    List<UserPermission> allServiceAccounts =
        serviceAccountProvider.getAll().stream()
            .map(ServiceAccount::toUserPermission)
            .collect(Collectors.toList());
    if (roles == null || roles.isEmpty()) {
      return allServiceAccounts.stream()
          .collect(Collectors.toMap(UserPermission::getId, Function.identity()));
    } else {
      return allServiceAccounts.stream()
          .filter(p -> p.getRoles().stream().map(Role::getName).anyMatch(roles::contains))
          .collect(Collectors.toMap(UserPermission::getId, Function.identity()));
    }
  }

  private Map<String, UserPermission> getUserPermissions(List<String> roles) {
    if (roles == null || roles.isEmpty()) {
      return permissionsRepository.getAllById();
    } else {
      return permissionsRepository.getAllByRoles(roles);
    }
  }

  public long updateUserPermissions(Map<String, UserPermission> permissionsById) {
    if (permissionsById.remove(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME) != null) {
      timeIt(
          "syncAnonymous",
          () -> {
            permissionsRepository.put(permissionsResolver.resolveUnrestrictedUser());
            log.info("Synced anonymous user role.");
          });
    }

    List<ExternalUser> extUsers =
        permissionsById.values().stream()
            .map(
                permission ->
                    new ExternalUser()
                        .setId(permission.getId())
                        .setExternalRoles(
                            permission.getRoles().stream()
                                .filter(role -> role.getSource() == Role.Source.EXTERNAL)
                                .collect(Collectors.toList())))
            .collect(Collectors.toList());

    if (extUsers.isEmpty()) {
      log.info("Found no non-anonymous user roles to sync.");
      return 0;
    }

    long count =
        timeIt(
            "syncUsers",
            () -> {
              Collection<UserPermission> values = permissionsResolver.resolve(extUsers).values();
              values.forEach(permissionsRepository::put);
              return values.size();
            });
    log.info("Synced {} non-anonymous user roles.", count);
    return count;
  }

  private static String metricName(String name) {
    return "fiat.userRoles." + name;
  }

  private void timeIt(String timerName, Runnable theThing) {
    Callable<Object> c =
        () -> {
          theThing.run();
          return null;
        };
    timeIt(timerName, c);
  }

  private <T> T timeIt(String timerName, Callable<T> theThing) {
    long startTime = System.nanoTime();
    String cause = null;
    try {
      return theThing.call();
    } catch (RuntimeException re) {
      cause = re.getClass().getSimpleName();
      throw re;
    } catch (Exception ex) {
      cause = ex.getClass().getSimpleName();
      throw new RuntimeException(ex);
    } finally {
      boolean success = cause == null;
      Id timer = registry.createId(metricName(timerName)).withTag("success", success);
      registry
          .timer(success ? timer : timer.withTag("cause", cause))
          .record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
    }
  }
}
