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
import com.netflix.spinnaker.cats.agent.StartupConcurrencyControl;
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
import io.kubernetes.client.util.ClientBuilder;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
public class KubernetesStreamingCachingAgentExecution implements LongRunningAgentExecution {

  private static final String METRIC_PREFIX = "kubernetes.agent.streaming.execution";

  private final KubernetesNamedAccountCredentials namedAccountCredentials;
  private final KubernetesStreamingCachingProperties cachingProperties;
  private final ProviderCache cache;
  private final List<KubernetesKind> kubernetesKinds;

  private final Registry registry;
  private final RetrySupport retrySupport = new RetrySupport();
  private final AtomicReference<LifecycleSnapshot> lifecycle =
      new AtomicReference<>(LifecycleSnapshot.notRunning());

  private final Id queueSize;
  private final Id queueRemainingCapacity;
  private final Id bulkedQueueSize;
  private final Id bulkedQueueRemainingCapacity;
  private final Timer elapsedTime;

  private final Counter batchesProcessed;
  private final Timer batchProcessingTime;
  private final Timer cacheSaveTime;
  private final StartupConcurrencyControl concurrencyControl;
  private final ExecutorService cleanupExecutorService;

  public KubernetesStreamingCachingAgentExecution(
      KubernetesNamedAccountCredentials namedAccountCredentials,
      ProviderCache cache,
      List<KubernetesKind> kubernetesKinds,
      Registry registry,
      StartupConcurrencyControl concurrencyControl,
      ExecutorService cleanupExecutorService) {
    this.namedAccountCredentials = namedAccountCredentials;
    this.cache = cache;
    this.kubernetesKinds = kubernetesKinds;
    this.cachingProperties = namedAccountCredentials.getStreamingCaching();
    this.registry = registry;
    this.concurrencyControl = concurrencyControl;
    this.cleanupExecutorService = cleanupExecutorService;

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
  public StartupConcurrencyControl getConcurrencyControl() {
    return concurrencyControl;
  }

  @Override
  public LongRunningAgentExecutionState getState() {
    LifecycleSnapshot snapshot = lifecycle.get();
    switch (snapshot.phase) {
      case STARTING:
        log.debug(
            "KubernetesStreaming caching agent {} startup is in progress",
            namedAccountCredentials.getCredentials().getAccountName());
        return LongRunningAgentExecutionState.RUNNING;
      case CLEANING_UP:
        return LongRunningAgentExecutionState.CLEANING_UP;
      case NOT_RUNNING:
        log.debug(
            "KubernetesStreaming caching agent {} is not running (no state)",
            namedAccountCredentials.getCredentials().getAccountName());
        return LongRunningAgentExecutionState.NOT_RUNNING;
      case RUNNING:
        break;
      default:
        throw new IllegalStateException("Unknown lifecycle phase " + snapshot.phase);
    }

    LongRunningAgentExecutionState status =
        snapshot.state.getState(
            cachingProperties.getReadinessTimeoutMillis(),
            cachingProperties.getLivenessTimeoutMillis());

    log.debug("KubernetesStreaming caching agent state: {}, status: {}", snapshot.state, status);
    return status;
  }

  @Override
  public long getStopTimeoutMillis() {
    return cachingProperties.getStopTimeoutMillis();
  }

  @Override
  public CompletableFuture<Void> stopExecutingAndCleanup() {
    String accountName = namedAccountCredentials.getCredentials().getAccountName();
    log.info("Stopping Kubernetes streaming agent execution for {}", accountName);

    LifecycleSnapshot current;
    CompletableFuture<Void> ownerFuture;
    LifecycleSnapshot cleaningUp;
    synchronized (this) {
      current = lifecycle.get();
      if (current.phase == LifecyclePhase.CLEANING_UP) {
        return current.cleanupFuture;
      }

      if (current.phase == LifecyclePhase.NOT_RUNNING) {
        // Clean stale metrics even if startup failed before publishing a running state.
        unregisterPolledMeterMetrics(
            registry,
            queueSize,
            queueRemainingCapacity,
            bulkedQueueSize,
            bulkedQueueRemainingCapacity);
        log.info(
            "KubernetesStreaming caching agent {} execution is not running, nothing to stop",
            accountName);
        return CompletableFuture.completedFuture(null);
      }
      if (current.phase != LifecyclePhase.RUNNING) {
        throw new IllegalStateException(
            "Cannot stop execution in lifecycle phase " + current.phase);
      }

      ownerFuture = new CompletableFuture<>();
      cleaningUp = LifecycleSnapshot.cleaningUp(current.state, ownerFuture);
      lifecycle.set(cleaningUp);
    }

    try {
      cleanupExecutorService.execute(
          () -> runCleanup(accountName, current.state, cleaningUp, ownerFuture));
    } catch (RuntimeException | Error submissionFailure) {
      log.warn(
          "Failed to submit KubernetesStreaming caching agent {} cleanup; running it synchronously",
          accountName,
          submissionFailure);
      runCleanup(accountName, current.state, cleaningUp, ownerFuture);
    }
    return ownerFuture;
  }

  private void runCleanup(
      String accountName,
      State state,
      LifecycleSnapshot cleaningUp,
      CompletableFuture<Void> ownerFuture) {
    if (!cleaningUp.cleanupStarted.compareAndSet(false, true)) {
      return;
    }
    Throwable cleanupFailure = null;
    log.info("Stopping KubernetesStreaming caching agent {} execution", accountName);
    try {
      unregisterPolledMeterMetrics(
          registry,
          queueSize,
          queueRemainingCapacity,
          bulkedQueueSize,
          bulkedQueueRemainingCapacity);
    } catch (RuntimeException | Error e) {
      cleanupFailure = e;
    }
    try {
      long stopTimeoutMillis = getStopTimeoutMillis();
      long timeout = stopTimeoutMillis > 1_000L ? stopTimeoutMillis - 1_000L : 0;
      boolean stopped = state.stopAndWait(timeout);
      if (!stopped) {
        log.warn(
            "KubernetesStreaming caching agent {} did not terminate in {}ms. Continue anyway",
            accountName,
            stopTimeoutMillis);
      }
    } catch (InterruptedException e) {
      log.warn("Interrupted while waiting for executor to terminate, {}", accountName, e);
      Thread.currentThread().interrupt();
    } catch (RuntimeException | Error e) {
      if (cleanupFailure == null) {
        cleanupFailure = e;
      } else if (cleanupFailure != e) {
        cleanupFailure.addSuppressed(e);
      }
    } finally {
      log.info("KubernetesStreaming caching agent {} stopped", accountName);
    }

    lifecycle.compareAndSet(cleaningUp, LifecycleSnapshot.notRunning());
    if (cleanupFailure == null) {
      log.info("KubernetesStreaming caching agent {} execution stopped successfully", accountName);
      ownerFuture.complete(null);
    } else {
      log.warn(
          "Error while stopping KubernetesStreaming caching agent {} execution",
          accountName,
          cleanupFailure);
      ownerFuture.completeExceptionally(cleanupFailure);
    }
  }

  @Override
  public void executeAgent(Agent agent) {
    log.info("Starting KubernetesStreaming caching agent {} execution", agent.getAgentType());
    CompletableFuture<Void> future = null;
    synchronized (this) {
      LifecycleSnapshot current = lifecycle.get();
      if (current.phase != LifecyclePhase.NOT_RUNNING) {
        log.warn(
            "KubernetesStreaming caching agent {} is already running. Skip this execution",
            agent.getAgentType());
        future = CompletableFuture.completedFuture(null);
      } else {
        LifecycleSnapshot starting = LifecycleSnapshot.starting();
        lifecycle.set(starting);
        try {
          future = startExecution(agent);
        } catch (RuntimeException | Error e) {
          lifecycle.compareAndSet(starting, LifecycleSnapshot.notRunning());
          log.error(
              "Failed to start Kubernetes streaming caching agent {}", agent.getAgentType(), e);
          throw e;
        }
      }
    }
    log.info("KubernetesStreaming caching agent {} execution started", agent.getAgentType());

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
    validateWatchHeartbeatInterval();

    ApiClient client = createApiClient();
    Set<APIResource> k8sResources = loadKubernetesResources(client);
    client.setReadTimeout(cachingProperties.getWatchHeartbeatIntervalMillis());

    ThreadFactory threadFactory =
        new ThreadFactoryBuilder()
            .setNameFormat("KubernetesStreamingCachingAgentExecutionThread-%d")
            .build();
    ExecutorService executorService = Executors.newCachedThreadPool(threadFactory);
    KubernetesStreamingWatcherFactory factory = createWatcherFactory(client, executorService);

    OkHttpClient httpClient = client.getHttpClient();
    State cachingState = new State(agent.getAgentType(), executorService, factory, httpClient);

    BlockingQueue<KubernetesStreamingEvent> queue =
        new ArrayBlockingQueue<>(cachingProperties.getEventQueueCapacity());
    BlockingQueue<List<KubernetesStreamingEvent>> bulkedQueue =
        new ArrayBlockingQueue<>(cachingProperties.getBulkedEventQueueCapacity());

    try {
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
              createEventProcessor(bulkedQueue, cachingState, agent), executorService);
      cachingState.monitorWorkers(batcherFuture, processorFuture);

      initWatchers(
          agent,
          cachingState,
          k8sResources,
          client,
          queue,
          cachingProperties.getWatcherRetryTimeoutMillis(),
          cachingProperties.getListTimeoutSeconds(),
          cachingProperties.getWatchTimeoutSeconds());
      factory.startAllWatchers();

      cachingState.start();
      lifecycle.set(LifecycleSnapshot.running(cachingState));

      return CompletableFuture.allOf(batcherFuture, processorFuture);
    } catch (RuntimeException | Error startupFailure) {
      cleanupFailedStartup(cachingState, startupFailure);
      throw startupFailure;
    }
  }

  protected KubernetesStreamingWatcherFactory createWatcherFactory(
      ApiClient client, ExecutorService executorService) {
    return new KubernetesStreamingWatcherFactory(
        client,
        namedAccountCredentials.getCredentials().getAccountName(),
        cachingProperties.getListPaginationSize(),
        executorService,
        concurrencyControl);
  }

  private void cleanupFailedStartup(State cachingState, Throwable startupFailure) {
    String accountName = namedAccountCredentials.getCredentials().getAccountName();
    try {
      boolean stopped = cachingState.stopAndWait(getStopTimeoutMillis());
      if (!stopped) {
        recordStartupCleanupFailure(
            startupFailure,
            new IllegalStateException(
                "Kubernetes streaming caching agent startup resources did not terminate in "
                    + getStopTimeoutMillis()
                    + "ms"),
            accountName);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      recordStartupCleanupFailure(startupFailure, e, accountName);
    } catch (RuntimeException | Error e) {
      recordStartupCleanupFailure(startupFailure, e, accountName);
    } finally {
      try {
        unregisterPolledMeterMetrics(
            registry,
            queueSize,
            queueRemainingCapacity,
            bulkedQueueSize,
            bulkedQueueRemainingCapacity);
      } catch (RuntimeException | Error e) {
        recordStartupCleanupFailure(startupFailure, e, accountName);
      }
    }
  }

  private void recordStartupCleanupFailure(
      Throwable startupFailure, Throwable cleanupFailure, String accountName) {
    if (cleanupFailure != startupFailure) {
      startupFailure.addSuppressed(cleanupFailure);
    }
    log.warn(
        "Failed to fully clean up Kubernetes streaming caching agent {} after startup failure",
        accountName,
        cleanupFailure);
  }

  private void validateWatchHeartbeatInterval() {
    int listTimeoutSeconds = cachingProperties.getListTimeoutSeconds();
    long listTimeoutMillis = listTimeoutSeconds * 1_000L;
    int watchHeartbeatIntervalMillis = cachingProperties.getWatchHeartbeatIntervalMillis();
    long livenessTimeoutMillis = cachingProperties.getLivenessTimeoutMillis();
    if (listTimeoutSeconds < 0
        || watchHeartbeatIntervalMillis <= 0
        || listTimeoutMillis > watchHeartbeatIntervalMillis
        || watchHeartbeatIntervalMillis >= livenessTimeoutMillis) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid Kubernetes streaming caching timeout configuration: expected "
                  + "listTimeoutSeconds >= 0, 0 < watchHeartbeatIntervalMillis, and "
                  + "listTimeoutSeconds * 1000 <= watchHeartbeatIntervalMillis < "
                  + "livenessTimeoutMillis; but listTimeoutSeconds=%d, "
                  + "watchHeartbeatIntervalMillis=%d, livenessTimeoutMillis=%d",
              listTimeoutSeconds, watchHeartbeatIntervalMillis, livenessTimeoutMillis));
    }
  }

  protected ApiClient createApiClient() {
    String kubeconfigFile = namedAccountCredentials.getCredentials().getKubeconfigFile();
    String server = namedAccountCredentials.getCredentials().getServer();

    try {
      if (server == null && kubeconfigFile == null) {
        String accountName = namedAccountCredentials.getCredentials().getAccountName();
        throw new IllegalStateException(
            "Both kubeconfig and server are not set for account: " + accountName);
      }
      if (server != null && !server.isEmpty()) {
        return new ClientBuilder().setBasePath(server).build();
      }
      if (kubeconfigFile != null) {
        return Config.fromConfig(kubeconfigFile);
      }
      throw new IllegalStateException(
          "Unexpected error: Unable to create ApiClient. Either server or kubeconfigFile should be set.");
    } catch (Exception e) {
      throw new RuntimeException("Failed to create ApiClient in agent", e);
    }
  }

  Set<APIResource> loadKubernetesResources(ApiClient client) {
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
      int listTimeoutSeconds,
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
          listTimeoutSeconds,
          watchTimeoutSeconds);
      watcherCount++;
    }

    log.info(
        "KubernetesStreaming caching agent {}: {} watchers created",
        agent.getAgentType(),
        watcherCount);
  }

  void processEvents(
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

    cachingState.updateLastProcessedEventBatchTime(batch);
  }

  KubernetesQueueProcessor<List<KubernetesStreamingEvent>> createEventProcessor(
      BlockingQueue<List<KubernetesStreamingEvent>> queue, State cachingState, Agent agent) {
    return new KubernetesQueueProcessor<>(
        queue,
        batch -> processEvents(cachingState, (KubernetesStreamingCachingAgent) agent, batch),
        cachingState::recordFailedEventBatch);
  }

  private void unregisterPolledMeterMetrics(Registry registry, Id... ids) {
    for (Id id : ids) {
      PolledMeter.remove(registry, id);
    }
  }

  private enum LifecyclePhase {
    NOT_RUNNING,
    STARTING,
    RUNNING,
    CLEANING_UP
  }

  private static final class LifecycleSnapshot {
    private static final LifecycleSnapshot NOT_RUNNING =
        new LifecycleSnapshot(LifecyclePhase.NOT_RUNNING, null, null);
    private static final LifecycleSnapshot STARTING =
        new LifecycleSnapshot(LifecyclePhase.STARTING, null, null);

    private final LifecyclePhase phase;
    private final State state;
    private final CompletableFuture<Void> cleanupFuture;
    private final AtomicBoolean cleanupStarted = new AtomicBoolean();

    private LifecycleSnapshot(
        LifecyclePhase phase, State state, CompletableFuture<Void> cleanupFuture) {
      this.phase = phase;
      this.state = state;
      this.cleanupFuture = cleanupFuture;
    }

    private static LifecycleSnapshot notRunning() {
      return NOT_RUNNING;
    }

    private static LifecycleSnapshot starting() {
      return STARTING;
    }

    private static LifecycleSnapshot running(State state) {
      return new LifecycleSnapshot(LifecyclePhase.RUNNING, state, null);
    }

    private static LifecycleSnapshot cleaningUp(
        State state, CompletableFuture<Void> cleanupFuture) {
      return new LifecycleSnapshot(LifecyclePhase.CLEANING_UP, state, cleanupFuture);
    }
  }
}
