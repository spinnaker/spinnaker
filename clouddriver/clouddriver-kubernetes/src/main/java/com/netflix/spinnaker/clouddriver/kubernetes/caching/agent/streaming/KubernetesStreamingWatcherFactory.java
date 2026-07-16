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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class KubernetesStreamingWatcherFactory {

  private final ApiClient apiClient;
  private final ExecutorService threadPool;
  private final Map<Type, KubernetesStreamingWatcher> watchers;
  private final Map<KubernetesStreamingWatcher, Future> watchersFutures;
  private final String account;
  private final int paginationSize;
  private final StartupConcurrencyControl concurrencyControl;

  public KubernetesStreamingWatcherFactory(
      ApiClient apiClient,
      String account,
      int paginationSize,
      ExecutorService threadPool,
      StartupConcurrencyControl concurrencyControl) {
    this.threadPool = threadPool;
    this.apiClient = apiClient;
    this.account = account;
    this.paginationSize = paginationSize;
    this.watchers = new ConcurrentHashMap<>();
    this.watchersFutures = new ConcurrentHashMap<>();
    this.concurrencyControl = concurrencyControl;
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
      int watchTimeoutSeconds) {
    Type apiType = TypeToken.get(apiTypeClass).getType();

    apiClient.setReadTimeout(0); // for watch requests
    K8SListWatchAdapter adapter =
        new K8SListWatchAdapter(group, version, resourcePlural, apiClient);

    KubernetesStreamingWatcher watcher =
        new KubernetesStreamingWatcher(
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
            watchTimeoutSeconds,
            concurrencyControl);

    this.watchers.putIfAbsent(apiType, watcher);
    return watcher;
  }

  public void startAllWatchers() {
    for (KubernetesStreamingWatcher watcher : watchers.values()) {
      Future future = threadPool.submit(watcher);
      watchersFutures.put(watcher, future);
    }
  }

  public void stopAllRegisteredWatchers() {
    for (Future future : watchersFutures.values()) {
      future.cancel(true);
    }
  }
}
