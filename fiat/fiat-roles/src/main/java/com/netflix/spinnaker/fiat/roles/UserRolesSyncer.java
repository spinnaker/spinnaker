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
import com.netflix.spinnaker.fiat.config.UserRolesSyncerConfig;
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
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.lock.LockManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;
import redis.clients.jedis.commands.JedisCommands;

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
  private final RedisClientDelegate redisClientDelegate;
  private final UserRolesSyncerConfig configurationProperties;
  private final Synchronizer synchronizer;

  private final Registry registry;
  private final Gauge userRolesSyncCount;

  private static final String KEY_USER_ROLES = "user_roles";
  private static final String KEY_LAST_SYNC_TIME = "last_sync_time";
  private static final String KEY_COUNT = "count";

  private static final AtomicReference<CountDownLatch> globalLatch = new AtomicReference<>(null);
  private final AtomicReference<String> lastSyncTime = new AtomicReference<>(null);

  @Autowired
  public UserRolesSyncer(
      DiscoveryStatusListener discoveryStatusListener,
      Registry registry,
      LockManager lockManager,
      PermissionsRepository permissionsRepository,
      PermissionsResolver permissionsResolver,
      ResourceProvider<ServiceAccount> serviceAccountProvider,
      ResourceProvidersHealthIndicator healthIndicator,
      RedisClientDelegate redisClientDelegate,
      UserRolesSyncerConfig configurationProperties,
      Synchronizer synchronizer) {
    this.discoveryStatusListener = discoveryStatusListener;

    this.lockManager = lockManager;
    this.permissionsRepository = permissionsRepository;
    this.permissionsResolver = permissionsResolver;
    this.serviceAccountProvider = serviceAccountProvider;
    this.healthIndicator = healthIndicator;
    this.redisClientDelegate = redisClientDelegate;
    this.configurationProperties = configurationProperties;
    this.synchronizer = synchronizer;

    this.registry = registry;
    this.userRolesSyncCount = registry.gauge(metricName("syncCount"));
  }

  @Scheduled(fixedDelay = 30000L)
  public void schedule() {
    if (this.configurationProperties.getSyncDelayTimeoutMs() < 0
        || !discoveryStatusListener.isEnabled()) {
      log.warn(
          "User roles syncing is disabled (syncDelayMs: {}, isEnabled: {})",
          this.configurationProperties.getSyncDelayMs(),
          discoveryStatusListener.isEnabled());
      return;
    }

    LockManager.LockOptions lockOptions =
        new LockManager.LockOptions()
            .withLockName("Fiat.UserRolesSyncer".toLowerCase())
            .withMaximumLockDuration(
                Duration.ofMillis(
                    this.configurationProperties.getSyncDelayMs()
                        + this.configurationProperties.getSyncDelayTimeoutMs()))
            .withSuccessInterval(Duration.ofMillis(this.configurationProperties.getSyncDelayMs()))
            .withFailureInterval(
                Duration.ofMillis(this.configurationProperties.getSyncFailureDelayMs()));

    // process the response
    lockManager.acquireLock(
        lockOptions,
        () -> {
          try {
            timeIt("syncTime", () -> userRolesSyncCount.set(this.syncAndReturn(new ArrayList<>())));
            log.info("user roles synced");
          } catch (Exception e) {
            log.error("User roles synchronization failed", e);
            userRolesSyncCount.set(-1);
          }
        });
  }

  public long syncAndReturn(List<String> roles) {
    log.debug("Attempting to sync the following user roles: {}", roles);
    if (this.configurationProperties.getSynchronizationConfig().isEnabled()) {
      log.info("since synchronization is enabled, forcing a sync of all user roles");
      return doSynchronizedUserRolesSync(new ArrayList<>());
    } else {
      return syncUserRoles(roles);
    }
  }

  private long doSynchronizedUserRolesSync(List<String> roles) {
    // this is the timestamp at which a thread attempts to refresh
    long syncAttemptTime = System.currentTimeMillis();
    log.debug("Attempting to sync user roles at: {}", new Date(syncAttemptTime));

    LockManager.LockOptions lockOptions =
        new LockManager.LockOptions()
            .withLockName("Fiat.UserRolesSyncer.Synchronize".toLowerCase())
            .withMaximumLockDuration(
                Duration.ofMillis(
                    this.configurationProperties.getSynchronizationConfig().getMaxLockDurationMs()))
            .withSuccessInterval(
                Duration.ofMillis(
                    this.configurationProperties.getSynchronizationConfig().getSyncDelayMs()))
            .withFailureInterval(
                Duration.ofMillis(
                    this.configurationProperties
                        .getSynchronizationConfig()
                        .getSyncFailureDelayMs()));

    long count = 0;
    while (true) {
      boolean isSyncAllowed = false; // flag to control which thread can attempt a sync
      // since countdown latches can't be reset, we keep a pointer to a global latch so that others
      // can wait on it
      CountDownLatch localLatch;
      synchronized (this) {
        if (globalLatch.get() == null) {
          isSyncAllowed = true;
          globalLatch.set(new CountDownLatch(1));
        }
        localLatch = globalLatch.get();
      }

      boolean lockAcquired = false;
      if (isSyncAllowed) {
        try {
          // acquire redis lock to sync across fiat pods
          LockManager.AcquireLockResponse<Long> acquireLockResponse =
              lockManager.acquireLock(
                  lockOptions,
                  () -> {
                    //
                    if (lastSyncTime.get() != null
                        && Long.parseLong(lastSyncTime.get()) > syncAttemptTime) {
                      log.info(
                          "Not syncing since user roles lastSyncTime: {} is later than this thread's syncAttemptTime: {}",
                          new Date(Long.parseLong(lastSyncTime.get())),
                          new Date(syncAttemptTime));
                      // get the most recent count value
                      String lastKnownSyncCount =
                          redisClientDelegate.withCommandsClient(
                              (Function<JedisCommands, String>) c -> c.get(userRolesCountKey()));
                      return Long.parseLong(lastKnownSyncCount);
                    }
                    return syncUserRoles(roles);
                  });

          // update in-mem last sync time by getting the most recent value
          String redisSyncTime =
              redisClientDelegate.withCommandsClient(
                  (Function<JedisCommands, String>) c -> c.get(userRolesLastSyncTimeKey()));
          log.debug("obtained the following lastSyncTime from redis: {}", redisSyncTime);
          this.lastSyncTime.set(redisSyncTime);

          if (acquireLockResponse != null
              && acquireLockResponse.getLockStatus() == LockManager.LockStatus.ACQUIRED
              && acquireLockResponse.isReleased()) {
            count = acquireLockResponse.getOnLockAcquiredCallbackResult();
            lockAcquired = true;
          }
        } catch (Exception e) {
          log.warn("syncing user roles failed", e);
        } finally {
          // If the lock was not acquired (held by another pod or transient error) and the
          // authoritative lastSyncTime has not advanced past our syncAttemptTime, back off
          // before releasing the in-process latch. Keeping the latch held during the sleep
          // parks all other threads in this pod on localLatch.await() so they can't fire
          // off their own Redis lock attempts in parallel. Waiter threads that subsequently
          // wake up will re-check lastSyncTime and either return early or take a turn as
          // the next syncer (and pay their own backoff).
          if (!lockAcquired) {
            String currentLastSyncTime = lastSyncTime.get();
            boolean lastSyncAdvanced =
                currentLastSyncTime != null
                    && Long.parseLong(currentLastSyncTime) > syncAttemptTime;
            if (!lastSyncAdvanced) {
              long failureDelayMs =
                  this.configurationProperties.getSynchronizationConfig().getSyncFailureDelayMs();
              if (failureDelayMs > 0) {
                try {
                  Thread.sleep(failureDelayMs);
                } catch (InterruptedException ignored) {
                  Thread.currentThread().interrupt();
                }
              }
            }
          }
          synchronized (this) {
            localLatch.countDown(); // release all other threads waiting on this one
            globalLatch.set(null);
          }
        }

        if (lockAcquired) {
          break;
        }
        if (Thread.currentThread().isInterrupted()) {
          break;
        }
      } else {
        try {
          localLatch.await(); // all other threads will be waiting here

        } catch (Exception e) {
          log.warn("current thread was interrupted while waiting to obtain the latch", e);
        }
      }

      if (lastSyncTime.get() != null && Long.parseLong(lastSyncTime.get()) > syncAttemptTime) {
        log.info(
            "Not syncing since user roles lastSyncTime: {} is later than this thread's syncAttemptTime: {}",
            new Date(Long.parseLong(lastSyncTime.get())),
            new Date(syncAttemptTime));
        // get the most recent count value
        String lastKnownSyncCount =
            redisClientDelegate.withCommandsClient(
                (Function<JedisCommands, String>) c -> c.get(userRolesCountKey()));
        return Long.parseLong(lastKnownSyncCount);
      }
    }
    return count;
  }

  private long syncUserRoles(List<String> roles) {
    FixedBackOff backoff = new FixedBackOff();
    backoff.setInterval(this.configurationProperties.getRetryIntervalMs());
    backoff.setMaxAttempts(
        Math.floorDiv(
                this.configurationProperties.getSyncDelayTimeoutMs(),
                this.configurationProperties.getRetryIntervalMs())
            + 1);
    BackOffExecution backOffExec = backoff.start();

    // after this point the execution will get rescheduled
    final long timeout =
        System.currentTimeMillis() + this.configurationProperties.getSyncDelayTimeoutMs();

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
          log.error("sync aborted as backoff is exhausted or the timeout is exceeded", ex);
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
      Map<String, UserPermission> filteredServiceAccounts =
          allServiceAccounts.stream()
              .filter(p -> p.getRoles().stream().map(Role::getName).anyMatch(roles::contains))
              .collect(Collectors.toMap(UserPermission::getId, Function.identity()));

      log.debug(
          "found {} service accounts that match roles: {}", filteredServiceAccounts.size(), roles);
      return filteredServiceAccounts;
    }
  }

  private Map<String, UserPermission> getUserPermissions(List<String> roles) {
    Map<String, Set<Role>> rolesById;
    if (roles == null || roles.isEmpty()) {
      rolesById = permissionsRepository.getAllById();
    } else {
      rolesById = permissionsRepository.getAllByRoles(roles);
    }
    return rolesById.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e -> new UserPermission().setId(e.getKey()).setRoles(new HashSet<>(e.getValue()))));
  }

  public long syncServiceAccount(String serviceAccountId, List<String> roles) {
    return synchronizer.syncServiceAccount(serviceAccountId, roles);
  }

  public long updateUserPermissions(Map<String, UserPermission> permissionsById) {
    log.debug("updating permissions for {} users", permissionsById.size());
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

    long syncTime = System.currentTimeMillis();

    long count = 0;
    if (extUsers.isEmpty()) {
      log.info("Found no non-anonymous user roles to sync.");
    } else {
      count =
          timeIt(
              "syncUsers",
              () -> {
                Map<String, UserPermission> resolved = permissionsResolver.resolve(extUsers);
                permissionsRepository.putAllById(resolved);
                return resolved.size();
              });
    }

    redisClientDelegate.withCommandsClient(
        c -> {
          log.debug("setting last sync time for user roles to {}", syncTime);
          c.set(userRolesLastSyncTimeKey(), String.valueOf(syncTime));
        });
    this.lastSyncTime.set(String.valueOf(syncTime));

    long finalCount = count;
    redisClientDelegate.withCommandsClient(
        c -> {
          log.debug("setting count for user roles to {}", finalCount);
          c.set(userRolesCountKey(), String.valueOf(finalCount));
        });

    log.info("Synced {} non-anonymous user roles.", finalCount);
    return finalCount;
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

  private String userRolesLastSyncTimeKey() {
    return String.format(
        "%s:%s:%s",
        this.configurationProperties.getSynchronizationConfig().getPrefix(),
        KEY_USER_ROLES,
        KEY_LAST_SYNC_TIME);
  }

  private String userRolesCountKey() {
    return String.format(
        "%s:%s:%s",
        this.configurationProperties.getSynchronizationConfig().getPrefix(),
        KEY_USER_ROLES,
        KEY_COUNT);
  }
}
