/*
 * Copyright 2025 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming;

import com.google.gson.reflect.TypeToken;
import com.netflix.spinnaker.cats.agent.StartupConcurrencyControl;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KubernetesStreamingWatcherFactory {

  private enum Lifecycle {
    REGISTERING,
    STARTED,
    STOPPED
  }

  private static final class WatcherRegistration {
    private final KubernetesStreamingWatcher watcher;
    private Future<?> future;

    private WatcherRegistration(KubernetesStreamingWatcher watcher) {
      this.watcher = watcher;
    }
  }

  private final ApiClient apiClient;
  private final ExecutorService threadPool;
  private final Object lifecycleLock = new Object();
  private final Map<Type, WatcherRegistration> registrations;
  private final String account;
  private final int paginationSize;
  private final StartupConcurrencyControl concurrencyControl;
  private final LongSupplier tickerMillis;
  private Lifecycle lifecycle = Lifecycle.REGISTERING;

  public KubernetesStreamingWatcherFactory(
      ApiClient apiClient,
      String account,
      int paginationSize,
      ExecutorService threadPool,
      StartupConcurrencyControl concurrencyControl) {
    this(
        apiClient,
        account,
        paginationSize,
        threadPool,
        concurrencyControl,
        KubernetesStreamingWatcher.systemTickerMillis());
  }

  KubernetesStreamingWatcherFactory(
      ApiClient apiClient,
      String account,
      int paginationSize,
      ExecutorService threadPool,
      StartupConcurrencyControl concurrencyControl,
      LongSupplier tickerMillis) {
    this.threadPool = threadPool;
    this.apiClient = apiClient;
    this.account = account;
    this.paginationSize = paginationSize;
    this.registrations = new HashMap<>();
    this.concurrencyControl = concurrencyControl;
    this.tickerMillis = tickerMillis;
  }

  public KubernetesStreamingWatcher watcherFor(
      Class<DynamicKubernetesObject> apiTypeClass,
      String kind,
      String group,
      String version,
      String resourcePlural,
      State state,
      BlockingQueue<KubernetesStreamingEvent> queue,
      Set<Keys.InfrastructureCacheKey> knownKeys,
      int watcherRetryTimeoutMillis,
      int listTimeoutSeconds,
      int watchTimeoutSeconds) {
    Type apiType = TypeToken.get(apiTypeClass).getType();
    synchronized (lifecycleLock) {
      if (lifecycle != Lifecycle.REGISTERING) {
        throw new IllegalStateException(
            "Cannot register Kubernetes watcher after watchers have "
                + (lifecycle == Lifecycle.STARTED ? "started" : "stopped"));
      }

      WatcherRegistration existing = registrations.get(apiType);
      if (existing != null) {
        return existing.watcher;
      }

      K8SListWatchAdapter adapter =
          new K8SListWatchAdapter(group, version, resourcePlural, apiClient);
      KubernetesStreamingWatcher watcher =
          createWatcher(
              adapter,
              state,
              kind,
              group,
              version,
              queue,
              knownKeys,
              watcherRetryTimeoutMillis,
              listTimeoutSeconds,
              watchTimeoutSeconds,
              tickerMillis);
      registrations.put(apiType, new WatcherRegistration(watcher));
      return watcher;
    }
  }

  KubernetesStreamingWatcher createWatcher(
      K8SListWatchAdapter adapter,
      State state,
      String kind,
      String group,
      String version,
      BlockingQueue<KubernetesStreamingEvent> queue,
      Set<Keys.InfrastructureCacheKey> knownKeys,
      int watcherRetryTimeoutMillis,
      int listTimeoutSeconds,
      int watchTimeoutSeconds,
      LongSupplier tickerMillis) {
    return new KubernetesStreamingWatcher(
        adapter,
        state,
        kind,
        group,
        version,
        account,
        paginationSize,
        queue,
        knownKeys,
        watcherRetryTimeoutMillis,
        listTimeoutSeconds,
        watchTimeoutSeconds,
        concurrencyControl,
        tickerMillis);
  }

  public void startAllWatchers() {
    synchronized (lifecycleLock) {
      if (lifecycle == Lifecycle.STARTED) {
        return;
      }
      if (lifecycle == Lifecycle.STOPPED) {
        throw new IllegalStateException(
            "Cannot start Kubernetes watchers after watchers have stopped");
      }

      lifecycle = Lifecycle.STARTED;
      try {
        for (WatcherRegistration registration : registrations.values()) {
          registration.future =
              Objects.requireNonNull(
                  threadPool.submit(registration.watcher), "Executor returned null watcher future");
        }
      } catch (RuntimeException | Error e) {
        lifecycle = Lifecycle.STOPPED;
        cancelRegisteredFutures();
        throw e;
      }
    }
  }

  public void stopAllRegisteredWatchers() {
    synchronized (lifecycleLock) {
      lifecycle = Lifecycle.STOPPED;
      cancelRegisteredFutures();
    }
  }

  boolean allWatchersHealthy(long livenessTimeoutMillis) {
    if (livenessTimeoutMillis <= 0) {
      throw new IllegalArgumentException(
          "Kubernetes watcher liveness timeout must be positive: " + livenessTimeoutMillis);
    }

    synchronized (lifecycleLock) {
      if (registrations.isEmpty()) {
        log.warn("Account {} has no registered Kubernetes streaming watchers", account);
        return false;
      }

      for (WatcherRegistration registration : registrations.values()) {
        KubernetesStreamingWatcher watcher = registration.watcher;
        Future<?> future = registration.future;
        if (future == null) {
          log.warn("Account {} watcher {} has no execution future", account, watcher.watcherId());
          return false;
        }
        boolean done = future.isDone();
        boolean cancelled = future.isCancelled();
        if (done || cancelled) {
          log.warn(
              "Account {} watcher {} has terminated (done={}, cancelled={})",
              account,
              watcher.watcherId(),
              done,
              cancelled);
          return false;
        }
      }

      IdentityHashMap<KubernetesStreamingWatcher, Long> heartbeatSnapshot = new IdentityHashMap<>();
      for (WatcherRegistration registration : registrations.values()) {
        KubernetesStreamingWatcher watcher = registration.watcher;
        if (!watcher.hasRecordedHeartbeat()) {
          log.warn(
              "Account {} watcher {} has not recorded a heartbeat", account, watcher.watcherId());
          return false;
        }
        heartbeatSnapshot.put(watcher, watcher.getLastHeartbeatTimeMillis());
      }

      long now = tickerMillis.getAsLong();
      for (Map.Entry<KubernetesStreamingWatcher, Long> heartbeat : heartbeatSnapshot.entrySet()) {
        KubernetesStreamingWatcher watcher = heartbeat.getKey();
        long lastHeartbeatTimeMillis = heartbeat.getValue();
        if (now < lastHeartbeatTimeMillis) {
          log.warn(
              "Account {} watcher {} detected ticker rollback: heartbeat {} ms, current time {} ms",
              account,
              watcher.watcherId(),
              lastHeartbeatTimeMillis,
              now);
          return false;
        }

        long heartbeatAgeMillis;
        try {
          heartbeatAgeMillis = Math.subtractExact(now, lastHeartbeatTimeMillis);
        } catch (ArithmeticException e) {
          log.warn(
              "Account {} watcher {} detected ticker elapsed time overflow: heartbeat {} ms, "
                  + "current time {} ms",
              account,
              watcher.watcherId(),
              lastHeartbeatTimeMillis,
              now);
          return false;
        }
        if (heartbeatAgeMillis >= livenessTimeoutMillis) {
          log.warn(
              "Account {} watcher {} is stale: heartbeat age {} ms, liveness limit {} ms",
              account,
              watcher.watcherId(),
              heartbeatAgeMillis,
              livenessTimeoutMillis);
          return false;
        }
      }
      return lifecycle == Lifecycle.STARTED;
    }
  }

  private void cancelRegisteredFutures() {
    for (WatcherRegistration registration : registrations.values()) {
      if (registration.future != null) {
        registration.future.cancel(true);
      }
    }
  }
}
