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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.agent.LongRunningAgentExecution;
import com.netflix.spinnaker.cats.agent.LongRunningAgentExecutionState;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.InfrastructureCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming.KubernetesStreamingEvent.Type;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesStreamingCachingProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiGroup;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.kork.core.RetrySupport;
import io.kubernetes.client.Discovery;
import io.kubernetes.client.Discovery.APIResource;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.ModelMapper;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KubernetesStreamingCachingAgentExecution implements LongRunningAgentExecution {

  private static final String METRIC_PREFIX = "kubernetes.agent.streaming.execution";

  private final KubernetesNamedAccountCredentials namedAccountCredentials;
  private final KubernetesStreamingCachingProperties cachingProperties;
  private final ProviderCache cache;
  private final List<KubernetesKind> kubernetesKinds;

  private final Registry registry;
  private final RetrySupport retrySupport = new RetrySupport();
  private final AtomicReference<State> state = new AtomicReference<>(null);

  private final Id queueSize;
  private final Id queueRemainingCapacity;
  private final Id bulkedQueueSize;
  private final Id bulkedQueueRemainingCapacity;
  private final Timer elapsedTime;

  private final Counter batchesProcessed;
  private final Timer batchProcessingTime;
  private final Timer cacheSaveTime;

  public KubernetesStreamingCachingAgentExecution(
      KubernetesNamedAccountCredentials namedAccountCredentials,
      ProviderCache cache,
      List<KubernetesKind> kubernetesKinds,
      Registry registry) {
    this.namedAccountCredentials = namedAccountCredentials;
    this.cache = cache;
    this.kubernetesKinds = kubernetesKinds;
    this.cachingProperties = namedAccountCredentials.getStreamingCaching();
    this.registry = registry;

    String account = namedAccountCredentials.getCredentials().getAccountName();
    this.queueSize =
        registry
            .createId(METRIC_PREFIX + ".queueSize")
            .withTag("account", account)
            .withTag("queueType", "events");
    this.queueRemainingCapacity =
        registry
            .createId(METRIC_PREFIX + ".queueRemainingCapacity")
            .withTag("account", account)
            .withTag("queueType", "events");
    this.bulkedQueueSize =
        registry
            .createId(METRIC_PREFIX + ".queueSize")
            .withTag("account", account)
            .withTag("queueType", "bulkedEvents");
    this.bulkedQueueRemainingCapacity =
        registry
            .createId(METRIC_PREFIX + ".queueRemainingCapacity")
            .withTag("account", account)
            .withTag("queueType", "bulkedEvents");

    this.elapsedTime = registry.timer(METRIC_PREFIX + ".elapsedTime", "account", account);
    this.batchesProcessed =
        registry.counter(METRIC_PREFIX + ".batchesProcessed", "account", account);
    this.cacheSaveTime = registry.timer(METRIC_PREFIX + ".cacheSaveTime", "account", account);
    this.batchProcessingTime =
        registry.timer(METRIC_PREFIX + ".batchProcessingTime", "account", account);
  }

  @Override
  public LongRunningAgentExecutionState getState() {
    State s = state.get();
    if (s == null) {
      return LongRunningAgentExecutionState.NOT_RUNNING;
    }
    return s.getState(
        cachingProperties.getReadinessTimeoutMillis(),
        cachingProperties.getLivenessTimeoutMillis());
  }

  @Override
  public long getStopTimeoutMillis() {
    return cachingProperties.getStopTimeoutMillis();
  }

  @Override
  public synchronized CompletableFuture<Void> stopExecutingAndCleanup() {
    State s = state.get();
    if (s == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.runAsync(
            () -> {
              try {
                long timeout = Math.max(0, getStopTimeoutMillis() - 1_000L);
                boolean stopped = s.stopAndWait(timeout);
                if (!stopped) {
                  log.warn(
                      "KubernetesStreaming caching agent did not terminate in {}ms. Continue anyway",
                      getStopTimeoutMillis());
                }
              } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for executor to terminate", e);
                Thread.currentThread().interrupt();
              }
            })
        .whenComplete(
            (result, e) -> {
              // unregister polled meter metrics
              unregisterPolledMeterMetrics(
                  registry,
                  queueSize,
                  queueRemainingCapacity,
                  bulkedQueueSize,
                  bulkedQueueRemainingCapacity);

              // clear state
              state.compareAndSet(s, null);
            });
  }

  @Override
  public void executeAgent(Agent agent) {
    CompletableFuture<Void> future = null;
    synchronized (this) {
      try {
        future = startExecution(agent);
      } catch (RuntimeException e) {
        log.error("Failed to start Kubernetes streaming caching agent {}", agent.getAgentType(), e);
        throw e;
      }
      if (future == null) {
        return;
      }
    }

    long startTime = System.nanoTime();
    try {
      future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AgentExecutionInterruptedException(
          String.format(
              "Agent execution interrupted while waiting for completion %s",
              namedAccountCredentials.getName()),
          e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      long endTime = System.nanoTime();
      elapsedTime.record(endTime - startTime, TimeUnit.NANOSECONDS);
    }
  }

  private CompletableFuture<Void> startExecution(Agent agent) {
    if (state.get() != null) {
      log.warn(
          "KubernetesStreaming caching agent {} is already running. Skip this execution",
          agent.getAgentType());
      return CompletableFuture.completedFuture(null);
    }

    ApiClient client = createApiClient();
    Set<APIResource> k8sResources = loadKubernetesResources(client);
    client.setReadTimeout(0); // for watch requests

    ThreadFactory threadFactory =
        new ThreadFactoryBuilder()
            .setNameFormat("KubernetesStreamingCachingAgentExecutionThread-%d")
            .build();
    ExecutorService executorService = Executors.newCachedThreadPool(threadFactory);
    KubernetesStreamingWatcherFactory factory =
        new KubernetesStreamingWatcherFactory(
            client, namedAccountCredentials.getCredentials().getAccountName(), executorService);
    State cachingState = new State(executorService, factory);

    BlockingQueue<KubernetesStreamingEvent> queue =
        new ArrayBlockingQueue<>(cachingProperties.getEventQueueCapacity());
    BlockingQueue<List<KubernetesStreamingEvent>> bulkedQueue =
        new ArrayBlockingQueue<>(cachingProperties.getBulkedEventQueueCapacity());

    PolledMeter.using(registry).withId(queueSize).monitorSize(queue);
    PolledMeter.using(registry).withId(bulkedQueueSize).monitorSize(bulkedQueue);
    PolledMeter.using(registry)
        .withId(queueRemainingCapacity)
        .monitorValue(queue, BlockingQueue::remainingCapacity);
    PolledMeter.using(registry)
        .withId(bulkedQueueRemainingCapacity)
        .monitorValue(bulkedQueue, BlockingQueue::remainingCapacity);

    CompletableFuture<Void> batcherFuture =
        CompletableFuture.runAsync(
            new KubernetesQueueBatcher<>(
                queue,
                bulkedQueue,
                cachingProperties.getBulkMaxEvents(),
                cachingProperties.getBulkMaxWaitMillis()),
            executorService);
    CompletableFuture<Void> processorFuture =
        CompletableFuture.runAsync(
            new KubernetesQueueProcessor<>(
                bulkedQueue,
                batch ->
                    processEvents(cachingState, (KubernetesStreamingCachingAgent) agent, batch)),
            executorService);

    initWatchers(
        agent,
        cachingState,
        k8sResources,
        client,
        queue,
        cachingProperties.getWatcherRetryTimeoutMillis(),
        cachingProperties.getWatchTimeoutSeconds());
    factory.startAllWatchers();

    cachingState.start();
    state.set(cachingState);

    return CompletableFuture.allOf(batcherFuture, processorFuture);
  }

  private ApiClient createApiClient() {
    String kubeconfigFile = namedAccountCredentials.getCredentials().getKubeconfigFile();
    if (kubeconfigFile == null) {
      String accountName = namedAccountCredentials.getCredentials().getAccountName();
      throw new IllegalStateException("Kubeconfig is not set for account: " + accountName);
    }

    try {
      return Config.fromConfig(kubeconfigFile);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create ApiClient in agent", e);
    }
  }

  private Set<APIResource> loadKubernetesResources(ApiClient client) {
    long startTime = System.nanoTime();
    boolean success = false;
    int connectTimeout = client.getConnectTimeout();
    int readTimeout = client.getReadTimeout();
    try {
      client.setConnectTimeout(cachingProperties.getKubeapiDiscoveryConnectionTimeoutMillis());
      client.setReadTimeout(cachingProperties.getKubeapiDiscoveryReadTimeoutMillis());
      Set<APIResource> resources =
          retrySupport.retry(
              () -> {
                try {
                  return ModelMapper.refresh(new Discovery(client));
                } catch (ApiException e) {
                  throw new RuntimeException("Failed to refresh Kubernetes discovery", e);
                }
              },
              cachingProperties.getKubeapiDiscoveryRetryLimit(),
              Duration.ofMillis(cachingProperties.getKubeapiDiscoveryRetryBackoffMillis()),
              cachingProperties.isKubeapiDiscoveryRetryExponential());
      success = true;
      return resources;
    } finally {
      long endTime = System.nanoTime();
      String account = namedAccountCredentials.getCredentials().getAccountName();
      registry
          .timer(
              METRIC_PREFIX + ".apiDiscoveryTime",
              "account",
              account,
              "success",
              String.valueOf(success))
          .record(endTime - startTime, TimeUnit.NANOSECONDS);

      // restore timeouts
      client.setConnectTimeout(connectTimeout);
      client.setReadTimeout(readTimeout);
    }
  }

  private void initWatchers(
      Agent agent,
      State cachingState,
      Set<APIResource> k8sResources,
      ApiClient client,
      BlockingQueue<KubernetesStreamingEvent> queue,
      int watcherRetryTimeoutMillis,
      int watchTimeoutSeconds) {
    KubernetesStreamingWatcherFactory factory = cachingState.getFactory();
    String accountName = namedAccountCredentials.getCredentials().getAccountName();

    Map<KubernetesKind, APIResource> kindToResource = new HashMap<>();
    int watcherCount = 0;
    for (APIResource resource : k8sResources) {
      String resourceKind = resource.getKind();
      if (resourceKind == null) {
        continue;
      }

      KubernetesApiGroup group = KubernetesApiGroup.fromString(resource.getGroup());
      KubernetesKind kind = KubernetesKind.from(resourceKind, group);
      if (group == KubernetesApiGroup.NONE || kind == KubernetesKind.NONE) {
        log.warn(
            "Agent: {}. Unknown kind: {}. Skip this resource", agent.getAgentType(), resourceKind);
        continue;
      }

      kindToResource.put(kind, resource);
    }

    for (KubernetesKind kind : kubernetesKinds) {
      APIResource apiResource = kindToResource.get(kind);
      if (apiResource == null) {
        log.warn("Agent: {}, No API resource found for kind: {}", agent.getAgentType(), kind);
        continue;
      }

      Set<InfrastructureCacheKey> existingObjects =
          cache.getIdentifiers(kind.toString()).stream()
              .map(Keys::parseKey)
              .filter(Optional::isPresent)
              .map(Optional::get)
              .filter(key -> key instanceof InfrastructureCacheKey)
              .map(key -> (InfrastructureCacheKey) key)
              .filter(key -> accountName.equals(key.getAccount()))
              .collect(Collectors.toSet());

      Class<DynamicKubernetesObject> apiTypeClass =
          (Class<DynamicKubernetesObject>)
              ModelMapper.getApiTypeClass(
                  apiResource.getGroup(), apiResource.getPreferredVersion(), apiResource.getKind());

      factory.watcherFor(
          apiTypeClass,
          apiResource.getKind(),
          apiResource.getGroup(),
          apiResource.getPreferredVersion(),
          apiResource.getResourcePlural(),
          cachingState,
          queue,
          existingObjects,
          watcherRetryTimeoutMillis,
          watchTimeoutSeconds);
      watcherCount++;
    }

    log.info(
        "KubernetesStreaming caching agent {}: {} watchers created",
        agent.getAgentType(),
        watcherCount);
  }

  private void processEvents(
      State cachingState,
      KubernetesStreamingCachingAgent agent,
      List<KubernetesStreamingEvent> batch) {
    long startProcessingTime = System.nanoTime();

    batchesProcessed.increment(batch.size());

    List<KubernetesManifest> updated = new ArrayList<>();
    List<KubernetesManifest> deleted = new ArrayList<>();
    for (KubernetesStreamingEvent event : batch) {
      if (event.getType() == Type.UPSERT) {
        updated.add(event.getObject());
      } else if (event.getType() == Type.DELETE) {
        deleted.add(event.getObject());
      } else {
        log.warn("Agent: {}. Unknown event type: {}", agent.getAgentType(), event.getType());
      }
    }
    CacheResult cacheResult = agent.buildCacheResult(updated, deleted);

    long startCacheSaveTime = System.nanoTime();

    // save objects without relationships
    Map<String, Collection<CacheData>> objects = new HashMap<>();
    for (Entry<String, Collection<CacheData>> e : cacheResult.getCacheResults().entrySet()) {
      String type = e.getKey();
      List<CacheData> items =
          e.getValue().stream()
              .map(item -> new DefaultCacheData(item.getId(), item.getAttributes(), Map.of()))
              .collect(Collectors.toList());
      objects.put(type, items);
    }
    cache.putCacheResult(agent.getAgentType(), new ArrayList<>(), new DefaultCacheResult(objects));

    // save relationships
    cache.addCacheResult(agent.getAgentType(), new ArrayList<>(), cacheResult);

    // evict deleted items
    cacheResult.getEvictions().forEach(cache::evictDeletedItems);

    long endTime = System.nanoTime();
    cacheSaveTime.record(endTime - startCacheSaveTime, TimeUnit.NANOSECONDS);
    batchProcessingTime.record(endTime - startProcessingTime, TimeUnit.NANOSECONDS);

    cachingState.updateLastProcessedEventBatchTime();
  }

  private void unregisterPolledMeterMetrics(Registry registry, Id... ids) {
    for (Id id : ids) {
      PolledMeter.remove(registry, id);
    }
  }
}
