/*
 * Copyright 2022 Armory, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Component
@ConditionalOnExpression("${fiat.write-mode.enabled:true}")
public class Synchronizer {
  private final ResourceProvidersHealthIndicator healthIndicator;
  private final PermissionsResolver permissionsResolver;
  private final PermissionsRepository permissionsRepository;
  private final ResourceProvider<ServiceAccount> serviceAccountProvider;
  private final Registry registry;
  private final long retryIntervalMs;
  private final long syncDelayTimeoutMs;

  public Synchronizer(
      ResourceProvidersHealthIndicator healthIndicator,
      PermissionsResolver permissionsResolver,
      PermissionsRepository permissionsRepository,
      ResourceProvider<ServiceAccount> serviceAccountProvider,
      Registry registry,
      @Value("${fiat.write-mode.retry-interval-ms:10000}") long retryIntervalMs,
      @Value("${fiat.write-mode.sync-delay-ms:600000}") long syncDelayTimeoutMs) {
    this.retryIntervalMs = retryIntervalMs;
    this.syncDelayTimeoutMs = syncDelayTimeoutMs;
    this.permissionsResolver = permissionsResolver;
    this.permissionsRepository = permissionsRepository;
    this.serviceAccountProvider = serviceAccountProvider;
    this.registry = registry;
    this.healthIndicator = healthIndicator;
  }

  public long syncAndReturn(List<String> roles) {
    FixedBackOff backoff = new FixedBackOff();
    backoff.setInterval(this.retryIntervalMs);
    backoff.setMaxAttempts(Math.floorDiv(this.syncDelayTimeoutMs, this.retryIntervalMs) + 1);
    BackOffExecution backOffExec = backoff.start();

    // after this point the execution will get rescheduled
    final long timeout = System.currentTimeMillis() + this.syncDelayTimeoutMs;

    if (!this.isServerHealthy()) {
      log.warn(
          "Server is currently UNHEALTHY. User permission role synchronization and "
              + "resolution may not complete until this server becomes healthy again.");
    }

    // Ensure we're going to reload app and service account definitions
    permissionsResolver.clearCache();

    while (true) {
      try {
        Map<String, Set<Role>> combo = new HashMap<>();
        // force a refresh of the unrestricted user in case the backing repository is empty:
        combo.put(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME, new HashSet<>());
        Map<String, Set<Role>> temp;
        boolean userPermissionsExists = (temp = this.getUserPermissions(roles)).isEmpty();
        if (!userPermissionsExists) {
          combo.putAll(temp);
        }
        boolean serviceAccountsExists = (temp = this.getServiceAccountsAsMap(roles)).isEmpty();
        if (!serviceAccountsExists) {
          combo.putAll(temp);
        }

        return this.updateUserPermissions(combo);
      } catch (ProviderException | PermissionResolutionException ex) {
        this.registry
            .counter(metricName("syncFailure"), "cause", ex.getClass().getSimpleName())
            .increment();
        Status status = this.healthIndicator.health().getStatus();
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
        this.isServerHealthy();
      }
    }
  }

  public long syncServiceAccount(String serviceAccountId, List<String> roles) {
    // So that fiat will pull fresh permissions
    permissionsResolver.clearCache();

    // Store this service account as a user with associated resources via `resolveAndMerge`.
    List<Role> convertedRoles =
        roles.stream()
            .map(extRole -> new Role().setSource(Role.Source.EXTERNAL).setName(extRole))
            .collect(Collectors.toList());
    ExternalUser serviceAccount =
        new ExternalUser().setId(serviceAccountId.toLowerCase()).setExternalRoles(convertedRoles);
    UserPermission serviceAccountPermissions = permissionsResolver.resolveAndMerge(serviceAccount);
    permissionsRepository.put(serviceAccountPermissions);

    // Resync resource for all users with the same roles (incl the requested service account),
    // so that they may have permissions to act on this service account
    // Note that this is not an atomic read/write operation, but they will be synced again
    // during the next periodic sync.
    // The chance of collisions are quite low, but if it does occur often we should create
    // a specific update operation on the permissionsRepository.
    Map<String, Collection<Role>> allUsersForRoles =
        permissionsRepository.getAllByRoles(roles).entrySet().stream()
            .filter(
                it ->
                    it.getValue().stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet())
                        .containsAll(roles))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    Map<String, UserPermission> resolvedUsers =
        permissionsResolver.resolveResources(allUsersForRoles);
    permissionsRepository.putAllById(resolvedUsers);
    return allUsersForRoles.size();
  }

  private boolean isServerHealthy() {
    return healthIndicator.health().getStatus() == Status.UP;
  }

  private Map<String, Set<Role>> getUserPermissions(List<String> roles) {
    if (roles == null || roles.isEmpty()) {
      return permissionsRepository.getAllById();
    } else {
      return permissionsRepository.getAllByRoles(roles);
    }
  }

  private long updateUserPermissions(Map<String, Set<Role>> rolesById) {
    if (rolesById.remove(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME) != null) {
      timeIt(
          "syncAnonymous",
          () -> {
            permissionsRepository.put(permissionsResolver.resolveUnrestrictedUser());
            log.info("Synced anonymous user role.");
          });
    }

    List<ExternalUser> extUsers =
        rolesById.entrySet().stream()
            .map(
                entry ->
                    new ExternalUser()
                        .setId(entry.getKey())
                        .setExternalRoles(
                            entry.getValue().stream()
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
              Map<String, UserPermission> values = permissionsResolver.resolve(extUsers);
              permissionsRepository.putAllById(values);
              return values.size();
            });
    log.info("Synced {} non-anonymous user roles.", count);
    return count;
  }

  private Map<String, Set<Role>> getServiceAccountsAsMap(List<String> roles) {
    List<UserPermission> allServiceAccounts =
        serviceAccountProvider.getAll().stream()
            .map(ServiceAccount::toUserPermission)
            .collect(Collectors.toList());
    if (roles == null || roles.isEmpty()) {
      return allServiceAccounts.stream()
          .collect(Collectors.toMap(UserPermission::getId, UserPermission::getRoles));
    } else {
      return allServiceAccounts.stream()
          .filter(p -> p.getRoles().stream().map(Role::getName).anyMatch(roles::contains))
          .collect(Collectors.toMap(UserPermission::getId, UserPermission::getRoles));
    }
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

  private static String metricName(String name) {
    return "fiat.userRoles." + name;
  }
}
