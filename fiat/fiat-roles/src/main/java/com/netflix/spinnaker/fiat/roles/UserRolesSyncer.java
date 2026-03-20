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
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.lock.LockManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnExpression("${fiat.write-mode.enabled:true}")
public class UserRolesSyncer {
  private final DiscoveryStatusListener discoveryStatusListener;
  private final LockManager lockManager;
  private final long syncDelayMs;
  private final long syncFailureDelayMs;
  private final long syncDelayTimeoutMs;
  private final String lockName;
  private final Registry registry;
  private final Gauge userRolesSyncCount;
  private final UserRolesSyncStrategy syncStrategy;

  @Autowired
  public UserRolesSyncer(
      DiscoveryStatusListener discoveryStatusListener,
      Registry registry,
      LockManager lockManager,
      UserRolesSyncStrategy syncStrategy,
      @Value("${fiat.write-mode.sync-delay-ms:600000}") long syncDelayMs,
      @Value("${fiat.write-mode.sync-failure-delay-ms:600000}") long syncFailureDelayMs,
      @Value("${fiat.write-mode.sync-delay-timeout-ms:30000}") long syncDelayTimeoutMs,
      @Value("${fiat.write-mode.lock-name:}") String lockName) {
    this.discoveryStatusListener = discoveryStatusListener;

    this.lockManager = lockManager;
    this.syncDelayMs = syncDelayMs;
    this.syncFailureDelayMs = syncFailureDelayMs;
    this.syncDelayTimeoutMs = syncDelayTimeoutMs;
    this.lockName = lockName;
    this.registry = registry;
    this.userRolesSyncCount = registry.gauge(metricName("syncCount"));
    this.syncStrategy = syncStrategy;
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
            timeIt(
                "syncTime",
                () -> userRolesSyncCount.set(syncStrategy.syncAndReturn(new ArrayList<>())));
          } catch (Exception e) {
            log.error("User roles synchronization failed", e);
            userRolesSyncCount.set(-1);
          }
        });
  }

  public long syncAndReturn(List<String> roles) {
    return syncStrategy.syncAndReturn(roles);
  }

  public long syncServiceAccount(String serviceAccountId, List<String> roles) {
    return syncStrategy.syncServiceAccount(serviceAccountId, roles);
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
