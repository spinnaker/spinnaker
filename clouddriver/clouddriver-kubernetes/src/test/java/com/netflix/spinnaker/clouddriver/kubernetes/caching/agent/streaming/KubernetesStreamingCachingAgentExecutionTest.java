/*
 * Copyright 2026 Wise, PLC.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.agent.LongRunningAgentExecutionState;
import com.netflix.spinnaker.cats.agent.NoOpStartupConcurrencyControl;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesStreamingCachingProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import io.kubernetes.client.Discovery.APIResource;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class KubernetesStreamingCachingAgentExecutionTest {

  private KubernetesNamedAccountCredentials namedAccountCredentials;
  private KubernetesCredentials credentials;
  private KubernetesStreamingCachingProperties cachingProperties;
  private ProviderCache providerCache;
  private Agent agent;
  private ExecutorService agentExecutor;
  private ExecutorService cleanupExecutor;

  @BeforeEach
  void setUp() {
    cachingProperties = new KubernetesStreamingCachingProperties();
    credentials = mock(KubernetesCredentials.class);
    when(credentials.getAccountName()).thenReturn("test-account");

    namedAccountCredentials = mock(KubernetesNamedAccountCredentials.class);
    when(namedAccountCredentials.getCredentials()).thenReturn(credentials);
    when(namedAccountCredentials.getStreamingCaching()).thenReturn(cachingProperties);

    providerCache = mock(ProviderCache.class);
    agent = mock(Agent.class);
    when(agent.getAgentType()).thenReturn("test-agent");
    agentExecutor = Executors.newFixedThreadPool(2);
    cleanupExecutor = Executors.newSingleThreadExecutor();
  }

  @Test
  void heartbeatDefaultsAreCompatibleWithListAndLivenessTimeouts() {
    assertThat(cachingProperties.getWatchHeartbeatIntervalMillis()).isEqualTo(25_000);
    assertThat(cachingProperties.getLivenessTimeoutMillis()).isEqualTo(30_000);
    assertThat(cachingProperties.getWatchTimeoutSeconds()).isEqualTo(300);
    assertThat((long) cachingProperties.getWatchHeartbeatIntervalMillis())
        .isLessThan(cachingProperties.getLivenessTimeoutMillis());
    assertThat((long) cachingProperties.getWatchHeartbeatIntervalMillis())
        .isGreaterThanOrEqualTo((long) cachingProperties.getListTimeoutSeconds() * 1_000L);
  }

  @Test
  void rejectsHeartbeatBelowListTimeoutBeforeCreatingKubernetesClient() {
    cachingProperties.setListTimeoutSeconds(20);
    cachingProperties.setWatchHeartbeatIntervalMillis(19_999);
    cachingProperties.setLivenessTimeoutMillis(30_000);
    KubernetesStreamingCachingAgentExecution execution =
        createExecution(
            () -> {
              throw new AssertionError("Kubernetes client must not be created");
            });

    assertThatThrownBy(() -> execution.executeAgent(agent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("listTimeoutSeconds=20")
        .hasMessageContaining("watchHeartbeatIntervalMillis=19999")
        .hasMessageContaining("livenessTimeoutMillis=30000");
  }

  @Test
  void rejectsHeartbeatEqualToLivenessTimeoutBeforeCreatingKubernetesClient() {
    cachingProperties.setListTimeoutSeconds(20);
    cachingProperties.setWatchHeartbeatIntervalMillis(30_000);
    cachingProperties.setLivenessTimeoutMillis(30_000);
    KubernetesStreamingCachingAgentExecution execution =
        createExecution(
            () -> {
              throw new AssertionError("Kubernetes client must not be created");
            });

    assertThatThrownBy(() -> execution.executeAgent(agent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("listTimeoutSeconds=20")
        .hasMessageContaining("watchHeartbeatIntervalMillis=30000")
        .hasMessageContaining("livenessTimeoutMillis=30000");
  }

  @Test
  void rejectsZeroHeartbeatBeforeCreatingKubernetesClient() {
    cachingProperties.setListTimeoutSeconds(0);
    cachingProperties.setWatchHeartbeatIntervalMillis(0);
    cachingProperties.setLivenessTimeoutMillis(30_000);
    KubernetesStreamingCachingAgentExecution execution =
        createExecution(
            () -> {
              throw new AssertionError("Kubernetes client must not be created");
            });

    assertThatThrownBy(() -> execution.executeAgent(agent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("listTimeoutSeconds >= 0")
        .hasMessageContaining("0 < watchHeartbeatIntervalMillis")
        .hasMessageContaining("listTimeoutSeconds=0")
        .hasMessageContaining("watchHeartbeatIntervalMillis=0")
        .hasMessageContaining("livenessTimeoutMillis=30000");
  }

  @Test
  void rejectsNegativeListTimeoutBeforeCreatingKubernetesClient() {
    cachingProperties.setListTimeoutSeconds(-1);
    cachingProperties.setWatchHeartbeatIntervalMillis(25_000);
    cachingProperties.setLivenessTimeoutMillis(30_000);
    KubernetesStreamingCachingAgentExecution execution =
        createExecution(
            () -> {
              throw new AssertionError("Kubernetes client must not be created");
            });

    assertThatThrownBy(() -> execution.executeAgent(agent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("listTimeoutSeconds >= 0")
        .hasMessageContaining("0 < watchHeartbeatIntervalMillis")
        .hasMessageContaining("listTimeoutSeconds=-1")
        .hasMessageContaining("watchHeartbeatIntervalMillis=25000")
        .hasMessageContaining("livenessTimeoutMillis=30000");
  }

  @Test
  void appliesConfiguredHeartbeatReadTimeoutAfterDiscovery() throws Exception {
    int heartbeatIntervalMillis = 27_000;
    cachingProperties.setListTimeoutSeconds(0);
    cachingProperties.setWatchHeartbeatIntervalMillis(heartbeatIntervalMillis);
    ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost").build();
    KubernetesStreamingCachingAgentExecution execution =
        new KubernetesStreamingCachingAgentExecution(
            namedAccountCredentials,
            providerCache,
            List.of(),
            new NoopRegistry(),
            new NoOpStartupConcurrencyControl(),
            cleanupExecutor) {
          @Override
          protected ApiClient createApiClient() {
            return apiClient;
          }

          @Override
          Set<APIResource> loadKubernetesResources(ApiClient client) {
            return Set.of();
          }
        };

    Future<?> executionFuture = agentExecutor.submit(() -> execution.executeAgent(agent));
    try {
      awaitStatePublished(execution);
      assertThat(apiClient.getReadTimeout()).isEqualTo(heartbeatIntervalMillis);
    } finally {
      execution.stopExecutingAndCleanup().get(5, TimeUnit.SECONDS);
      executionFuture.get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void monitorsQueueWorkersBeforePublishingState() throws Exception {
    cachingProperties.setReadinessTimeoutMillis(0);
    cachingProperties.setListTimeoutSeconds(0);
    ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost").build();
    KubernetesStreamingCachingAgentExecution execution =
        new KubernetesStreamingCachingAgentExecution(
            namedAccountCredentials,
            providerCache,
            List.of(),
            new NoopRegistry(),
            new NoOpStartupConcurrencyControl(),
            cleanupExecutor) {
          @Override
          protected ApiClient createApiClient() {
            return apiClient;
          }

          @Override
          Set<APIResource> loadKubernetesResources(ApiClient client) {
            return Set.of();
          }

          @Override
          protected KubernetesStreamingWatcherFactory createWatcherFactory(
              ApiClient client, ExecutorService executorService) {
            return new AlwaysHealthyWatcherFactory(client, executorService);
          }
        };

    Future<?> executionFuture = agentExecutor.submit(() -> execution.executeAgent(agent));
    try {
      awaitStatePublished(execution);
      assertThat(execution.getState()).isEqualTo(LongRunningAgentExecutionState.RUNNING);
    } finally {
      execution.stopExecutingAndCleanup().get(5, TimeUnit.SECONDS);
      executionFuture.get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void accountsForProcessedBatchOnlyAfterAllCacheOperationsSucceed() {
    KubernetesStreamingCachingAgentExecution execution =
        createExecution(
            () -> {
              throw new AssertionError("Kubernetes client is not expected");
            });
    State cachingState = mock(State.class);
    KubernetesStreamingCachingAgent streamingAgent = mock(KubernetesStreamingCachingAgent.class);
    when(streamingAgent.getAgentType()).thenReturn("test-agent");
    DefaultCacheResult cacheResult =
        new DefaultCacheResult(Map.of(), Map.of("pods", List.of("deleted-pod")));
    when(streamingAgent.buildCacheResult(anyList(), anyList())).thenReturn(cacheResult);
    List<KubernetesStreamingEvent> batch =
        List.of(
            new KubernetesStreamingEvent(
                KubernetesStreamingEvent.Type.UPSERT, mock(KubernetesManifest.class)),
            new KubernetesStreamingEvent(
                KubernetesStreamingEvent.Type.DELETE, mock(KubernetesManifest.class)));

    execution.processEvents(cachingState, streamingAgent, batch);

    InOrder inOrder = inOrder(providerCache, cachingState);
    inOrder
        .verify(providerCache)
        .putCacheResult(eq("test-agent"), anyList(), any(DefaultCacheResult.class));
    inOrder.verify(providerCache).addCacheResult("test-agent", List.of(), cacheResult);
    inOrder.verify(providerCache).evictDeletedItems("pods", List.of("deleted-pod"));
    inOrder.verify(cachingState).updateLastProcessedEventBatchTime(same(batch));
  }

  @Test
  void doesNotAccountForBatchWhenTheLastCacheOperationFails() {
    KubernetesStreamingCachingAgentExecution execution =
        createExecution(
            () -> {
              throw new AssertionError("Kubernetes client is not expected");
            });
    State cachingState = mock(State.class);
    KubernetesStreamingCachingAgent streamingAgent = mock(KubernetesStreamingCachingAgent.class);
    when(streamingAgent.getAgentType()).thenReturn("test-agent");
    DefaultCacheResult cacheResult =
        new DefaultCacheResult(Map.of(), Map.of("pods", List.of("deleted-pod")));
    when(streamingAgent.buildCacheResult(anyList(), anyList())).thenReturn(cacheResult);
    RuntimeException cacheFailure = new RuntimeException("eviction failed");
    org.mockito.Mockito.doThrow(cacheFailure)
        .when(providerCache)
        .evictDeletedItems("pods", List.of("deleted-pod"));
    List<KubernetesStreamingEvent> batch =
        List.of(
            new KubernetesStreamingEvent(
                KubernetesStreamingEvent.Type.DELETE, mock(KubernetesManifest.class)));

    assertThatThrownBy(() -> execution.processEvents(cachingState, streamingAgent, batch))
        .isSameAs(cacheFailure);

    org.mockito.Mockito.verify(cachingState, never()).updateLastProcessedEventBatchTime(anyList());
  }

  @Test
  void eventProcessorAccountsForEveryFailedBatchAndContinues() throws Exception {
    KubernetesStreamingCachingAgentExecution execution =
        createExecution(
            () -> {
              throw new AssertionError("Kubernetes client is not expected");
            });
    State cachingState = mock(State.class);
    KubernetesStreamingCachingAgent streamingAgent = mock(KubernetesStreamingCachingAgent.class);
    RuntimeException cacheFailure = new RuntimeException("cache failed");
    when(streamingAgent.buildCacheResult(anyList(), anyList())).thenThrow(cacheFailure);
    List<KubernetesStreamingEvent> firstBatch =
        List.of(
            new KubernetesStreamingEvent(
                KubernetesStreamingEvent.Type.UPSERT, mock(KubernetesManifest.class)));
    List<KubernetesStreamingEvent> secondBatch =
        List.of(
            new KubernetesStreamingEvent(
                KubernetesStreamingEvent.Type.DELETE, mock(KubernetesManifest.class)));
    ArrayBlockingQueue<List<KubernetesStreamingEvent>> queue = new ArrayBlockingQueue<>(2);
    CountDownLatch failuresRecorded = new CountDownLatch(2);
    List<List<KubernetesStreamingEvent>> recordedBatches =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              recordedBatches.add(invocation.getArgument(0));
              failuresRecorded.countDown();
              return null;
            })
        .when(cachingState)
        .recordFailedEventBatch(anyList(), same(cacheFailure));
    KubernetesQueueProcessor<List<KubernetesStreamingEvent>> processor =
        execution.createEventProcessor(queue, cachingState, streamingAgent);
    Thread processorThread = new Thread(processor);
    processorThread.start();
    try {
      queue.put(firstBatch);
      queue.put(secondBatch);

      assertThat(failuresRecorded.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(recordedBatches).containsExactlyElementsOf(List.of(firstBatch, secondBatch));
      org.mockito.Mockito.verify(cachingState, never())
          .updateLastProcessedEventBatchTime(anyList());
    } finally {
      processorThread.interrupt();
      processorThread.join(5_000);
      assertThat(processorThread.isAlive()).isFalse();
    }
  }

  @Test
  void failedWatcherStartupCleansUnpublishedExecutionResources() throws Exception {
    RuntimeException startupFailure = new RuntimeException("watcher submission failed");
    ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost").build();
    DefaultRegistry registry = new DefaultRegistry();
    AtomicReference<ExecutorService> internalExecutor = new AtomicReference<>();
    AtomicReference<PartiallyFailingWatcherFactory> watcherFactory = new AtomicReference<>();
    KubernetesStreamingCachingAgentExecution execution =
        createFailingStartupExecution(
            apiClient, registry, startupFailure, null, false, internalExecutor, watcherFactory);

    try {
      Throwable thrown = catchThrowable(() -> execution.executeAgent(agent));

      assertThat(thrown).isSameAs(startupFailure);
      assertThat(internalExecutor.get().isTerminated()).isTrue();
      assertThat(watcherFactory.get().watcherFuture).isCancelled();
      assertThat(watcherFactory.get().watcherStopped.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(execution.getState()).isEqualTo(LongRunningAgentExecutionState.NOT_RUNNING);
      assertPolledQueueMetersRemoved(registry);
    } finally {
      PartiallyFailingWatcherFactory factory = watcherFactory.get();
      if (factory != null) {
        factory.forceStop();
      }
      ExecutorService executorService = internalExecutor.get();
      if (executorService != null) {
        executorService.shutdownNow();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
      }
    }
  }

  @Test
  void cleanupFailureIsSuppressedWithoutReplacingStartupFailure() throws Exception {
    RuntimeException startupFailure = new RuntimeException("watcher submission failed");
    RuntimeException cleanupFailure = new RuntimeException("watcher cleanup failed");
    ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost").build();
    AtomicReference<ExecutorService> internalExecutor = new AtomicReference<>();
    AtomicReference<PartiallyFailingWatcherFactory> watcherFactory = new AtomicReference<>();
    KubernetesStreamingCachingAgentExecution execution =
        createFailingStartupExecution(
            apiClient,
            new DefaultRegistry(),
            startupFailure,
            cleanupFailure,
            false,
            internalExecutor,
            watcherFactory);

    try {
      Throwable thrown = catchThrowable(() -> execution.executeAgent(agent));

      assertThat(thrown).isSameAs(startupFailure);
      assertThat(thrown.getSuppressed()).containsExactly(cleanupFailure);
      assertThat(internalExecutor.get().isTerminated()).isTrue();
      assertThat(watcherFactory.get().watcherFuture).isCancelled();
      assertThat(execution.getState()).isEqualTo(LongRunningAgentExecutionState.NOT_RUNNING);
    } finally {
      forceStop(watcherFactory.get(), internalExecutor.get());
    }
  }

  @Test
  void interruptedStartupCleanupPreservesInterruptAndOriginalFailure() throws Exception {
    RuntimeException startupFailure = new RuntimeException("watcher submission failed");
    ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost").build();
    AtomicReference<ExecutorService> internalExecutor = new AtomicReference<>();
    AtomicReference<PartiallyFailingWatcherFactory> watcherFactory = new AtomicReference<>();
    KubernetesStreamingCachingAgentExecution execution =
        createFailingStartupExecution(
            apiClient,
            new DefaultRegistry(),
            startupFailure,
            null,
            true,
            internalExecutor,
            watcherFactory);

    Throwable thrown;
    boolean interrupted;
    boolean executorTerminated;
    boolean watcherCancelled;
    try {
      thrown = catchThrowable(() -> execution.executeAgent(agent));
      interrupted = Thread.currentThread().isInterrupted();
      executorTerminated = internalExecutor.get().isTerminated();
      watcherCancelled = watcherFactory.get().watcherFuture.isCancelled();
    } finally {
      Thread.interrupted();
      forceStop(watcherFactory.get(), internalExecutor.get());
    }

    assertThat(thrown).isSameAs(startupFailure);
    assertThat(interrupted).isTrue();
    assertThat(executorTerminated).isTrue();
    assertThat(watcherCancelled).isTrue();
    assertThat(execution.getState()).isEqualTo(LongRunningAgentExecutionState.NOT_RUNNING);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    agentExecutor.shutdownNow();
    cleanupExecutor.shutdownNow();
    assertThat(agentExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    assertThat(cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void reportsRunningWithoutBlockingWhileStartupIsInProgress() throws Exception {
    CountDownLatch startupInProgress = new CountDownLatch(1);
    CountDownLatch allowStartupToFail = new CountDownLatch(1);
    KubernetesStreamingCachingAgentExecution execution =
        createExecution(
            () -> {
              startupInProgress.countDown();
              await(allowStartupToFail);
              throw new IllegalStateException("expected startup failure");
            });

    Future<?> executionFuture = agentExecutor.submit(() -> execution.executeAgent(agent));
    assertThat(startupInProgress.await(5, TimeUnit.SECONDS)).isTrue();
    try {
      Future<LongRunningAgentExecutionState> observedState =
          agentExecutor.submit(execution::getState);
      assertThat(observedState.get(1, TimeUnit.SECONDS))
          .isEqualTo(LongRunningAgentExecutionState.RUNNING);
    } finally {
      allowStartupToFail.countDown();
    }

    assertThatThrownBy(() -> executionFuture.get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("expected startup failure");
    assertThat(execution.getState()).isEqualTo(LongRunningAgentExecutionState.NOT_RUNNING);
  }

  @Test
  void reportsRunningWhileConcurrentCallerIsStarting() throws InterruptedException {
    CountDownLatch firstStartupInProgress = new CountDownLatch(1);
    CountDownLatch allowFirstStartupToFail = new CountDownLatch(1);
    CountDownLatch secondStartupInProgress = new CountDownLatch(1);
    CountDownLatch allowSecondStartupToFail = new CountDownLatch(1);
    AtomicInteger startupAttempt = new AtomicInteger();
    KubernetesStreamingCachingAgentExecution execution =
        createExecution(
            () -> {
              if (startupAttempt.incrementAndGet() == 1) {
                firstStartupInProgress.countDown();
                await(allowFirstStartupToFail);
                throw new IllegalStateException("expected first startup failure");
              }
              secondStartupInProgress.countDown();
              await(allowSecondStartupToFail);
              throw new IllegalStateException("expected second startup failure");
            });

    Future<?> firstExecution = agentExecutor.submit(() -> execution.executeAgent(agent));
    assertThat(firstStartupInProgress.await(5, TimeUnit.SECONDS)).isTrue();

    AtomicReference<Thread> secondExecutionThread = new AtomicReference<>();
    Future<?> secondExecution =
        agentExecutor.submit(
            () -> {
              secondExecutionThread.set(Thread.currentThread());
              execution.executeAgent(agent);
            });
    awaitThreadState(secondExecutionThread, Thread.State.BLOCKED);

    try {
      allowFirstStartupToFail.countDown();
      assertThat(secondStartupInProgress.await(5, TimeUnit.SECONDS)).isTrue();
      assertStartupFailure(firstExecution, "expected first startup failure");
      assertThat(execution.getState()).isEqualTo(LongRunningAgentExecutionState.RUNNING);
    } finally {
      allowFirstStartupToFail.countDown();
      allowSecondStartupToFail.countDown();
    }

    assertStartupFailure(secondExecution, "expected second startup failure");
    assertThat(execution.getState()).isEqualTo(LongRunningAgentExecutionState.NOT_RUNNING);
  }

  @Test
  void concurrentCleanupCallersShareOneOwnerUntilCleanupCompletes() throws Exception {
    cleanupExecutor.shutdownNow();
    assertThat(cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    cleanupExecutor = Executors.newFixedThreadPool(2);
    ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost").build();
    AtomicInteger clientCreations = new AtomicInteger();
    AtomicReference<BlockingCleanupWatcherFactory> watcherFactory = new AtomicReference<>();
    KubernetesStreamingCachingAgentExecution execution =
        new KubernetesStreamingCachingAgentExecution(
            namedAccountCredentials,
            providerCache,
            List.of(),
            new NoopRegistry(),
            new NoOpStartupConcurrencyControl(),
            cleanupExecutor) {
          @Override
          protected ApiClient createApiClient() {
            clientCreations.incrementAndGet();
            return apiClient;
          }

          @Override
          Set<APIResource> loadKubernetesResources(ApiClient client) {
            return Set.of();
          }

          @Override
          protected KubernetesStreamingWatcherFactory createWatcherFactory(
              ApiClient client, ExecutorService executorService) {
            BlockingCleanupWatcherFactory factory =
                new BlockingCleanupWatcherFactory(client, executorService);
            watcherFactory.set(factory);
            return factory;
          }
        };
    Future<?> executionFuture = agentExecutor.submit(() -> execution.executeAgent(agent));
    ExecutorService callers = Executors.newFixedThreadPool(2);
    CountDownLatch startCleanup = new CountDownLatch(1);
    CompletableFuture<Void> firstCleanup = null;
    CompletableFuture<Void> secondCleanup = null;
    try {
      awaitStatePublished(execution);
      AtomicReference<CompletableFuture<Void>> firstResult = new AtomicReference<>();
      AtomicReference<CompletableFuture<Void>> secondResult = new AtomicReference<>();
      Future<?> firstCaller =
          callers.submit(
              () -> {
                await(startCleanup);
                firstResult.set(execution.stopExecutingAndCleanup());
              });
      Future<?> secondCaller =
          callers.submit(
              () -> {
                await(startCleanup);
                secondResult.set(execution.stopExecutingAndCleanup());
              });

      startCleanup.countDown();
      firstCaller.get(5, TimeUnit.SECONDS);
      secondCaller.get(5, TimeUnit.SECONDS);
      firstCleanup = firstResult.get();
      secondCleanup = secondResult.get();
      assertThat(watcherFactory.get().cleanupStarted.await(5, TimeUnit.SECONDS)).isTrue();

      assertThat(secondCleanup).isSameAs(firstCleanup);
      assertThat(watcherFactory.get().stopCount).hasValue(1);
      assertThat(execution.getState()).isEqualTo(LongRunningAgentExecutionState.CLEANING_UP);

      execution.executeAgent(agent);
      assertThat(clientCreations).hasValue(1);
      assertThat(execution.getState()).isEqualTo(LongRunningAgentExecutionState.CLEANING_UP);

      watcherFactory.get().allowCleanup.countDown();
      firstCleanup.get(5, TimeUnit.SECONDS);
      assertThat(execution.getState()).isEqualTo(LongRunningAgentExecutionState.NOT_RUNNING);
      executionFuture.get(5, TimeUnit.SECONDS);
      assertThat(watcherFactory.get().stopCount).hasValue(1);
    } finally {
      startCleanup.countDown();
      BlockingCleanupWatcherFactory factory = watcherFactory.get();
      if (factory != null) {
        factory.allowCleanup.countDown();
      }
      awaitCleanup(firstCleanup);
      awaitCleanup(secondCleanup);
      callers.shutdownNow();
      assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void cleanupSubmissionFailureRunsBlockedFallbackBeforeCompletingSharedFuture() throws Exception {
    RejectOnceExecutorService rejectingCleanupExecutor = new RejectOnceExecutorService();
    ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost").build();
    DefaultRegistry registry = new DefaultRegistry();
    AtomicReference<BlockingCleanupWatcherFactory> watcherFactory = new AtomicReference<>();
    KubernetesStreamingCachingAgentExecution execution =
        new KubernetesStreamingCachingAgentExecution(
            namedAccountCredentials,
            providerCache,
            List.of(),
            registry,
            new NoOpStartupConcurrencyControl(),
            rejectingCleanupExecutor) {
          @Override
          protected ApiClient createApiClient() {
            return apiClient;
          }

          @Override
          Set<APIResource> loadKubernetesResources(ApiClient client) {
            return Set.of();
          }

          @Override
          protected KubernetesStreamingWatcherFactory createWatcherFactory(
              ApiClient client, ExecutorService executorService) {
            BlockingCleanupWatcherFactory factory =
                new BlockingCleanupWatcherFactory(client, executorService);
            watcherFactory.set(factory);
            return factory;
          }
        };
    Future<?> executionFuture = agentExecutor.submit(() -> execution.executeAgent(agent));
    ExecutorService callers = Executors.newFixedThreadPool(2);
    AtomicReference<CompletableFuture<Void>> firstResult = new AtomicReference<>();
    AtomicReference<CompletableFuture<Void>> secondResult = new AtomicReference<>();
    Future<?> firstCaller = null;
    try {
      awaitStatePublished(execution);

      firstCaller = callers.submit(() -> firstResult.set(execution.stopExecutingAndCleanup()));
      assertThat(watcherFactory.get().cleanupStarted.await(5, TimeUnit.SECONDS)).isTrue();
      Future<?> secondCaller =
          callers.submit(() -> secondResult.set(execution.stopExecutingAndCleanup()));
      secondCaller.get(5, TimeUnit.SECONDS);
      CompletableFuture<Void> ownerFuture = secondResult.get();

      assertThat(ownerFuture).isNotDone();
      assertThat(execution.getState()).isEqualTo(LongRunningAgentExecutionState.CLEANING_UP);
      assertThat(watcherFactory.get().stopCount).hasValue(1);

      watcherFactory.get().allowCleanup.countDown();
      firstCaller.get(5, TimeUnit.SECONDS);
      assertThat(firstResult.get()).isSameAs(ownerFuture);
      ownerFuture.get(5, TimeUnit.SECONDS);
      executionFuture.get(5, TimeUnit.SECONDS);
      assertThat(execution.getState()).isEqualTo(LongRunningAgentExecutionState.NOT_RUNNING);
      assertPolledQueueMetersRemoved(registry);
      assertThat(watcherFactory.get().stopCount).hasValue(1);
    } finally {
      BlockingCleanupWatcherFactory factory = watcherFactory.get();
      if (factory != null) {
        factory.allowCleanup.countDown();
      }
      if (!executionFuture.isDone()) {
        execution.stopExecutingAndCleanup().get(5, TimeUnit.SECONDS);
        executionFuture.get(5, TimeUnit.SECONDS);
      }
      if (firstCaller != null) {
        firstCaller.get(5, TimeUnit.SECONDS);
      }
      callers.shutdownNow();
      assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
      rejectingCleanupExecutor.shutdownNow();
      assertThat(rejectingCleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void cleanupFailureDuringRejectedSubmissionCompletesOwnerExceptionallyAfterStopping()
      throws Exception {
    RejectOnceExecutorService rejectingCleanupExecutor = new RejectOnceExecutorService();
    ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost").build();
    RuntimeException cleanupFailure = new RuntimeException("watcher cleanup failed");
    AtomicReference<BlockingCleanupWatcherFactory> watcherFactory = new AtomicReference<>();
    KubernetesStreamingCachingAgentExecution execution =
        new KubernetesStreamingCachingAgentExecution(
            namedAccountCredentials,
            providerCache,
            List.of(),
            new NoopRegistry(),
            new NoOpStartupConcurrencyControl(),
            rejectingCleanupExecutor) {
          @Override
          protected ApiClient createApiClient() {
            return apiClient;
          }

          @Override
          Set<APIResource> loadKubernetesResources(ApiClient client) {
            return Set.of();
          }

          @Override
          protected KubernetesStreamingWatcherFactory createWatcherFactory(
              ApiClient client, ExecutorService executorService) {
            BlockingCleanupWatcherFactory factory =
                new BlockingCleanupWatcherFactory(client, executorService, cleanupFailure);
            watcherFactory.set(factory);
            return factory;
          }
        };
    Future<?> executionFuture = agentExecutor.submit(() -> execution.executeAgent(agent));
    try {
      awaitStatePublished(execution);
      watcherFactory.get().allowCleanup.countDown();

      CompletableFuture<Void> ownerFuture = execution.stopExecutingAndCleanup();

      assertThatThrownBy(() -> ownerFuture.get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCause(cleanupFailure);
      executionFuture.get(5, TimeUnit.SECONDS);
      assertThat(execution.getState()).isEqualTo(LongRunningAgentExecutionState.NOT_RUNNING);
      assertThat(watcherFactory.get().stopCount).hasValue(1);
    } finally {
      watcherFactory.get().allowCleanup.countDown();
      if (!executionFuture.isDone()) {
        execution
            .stopExecutingAndCleanup()
            .handle((ignored, failure) -> null)
            .get(5, TimeUnit.SECONDS);
        executionFuture.get(5, TimeUnit.SECONDS);
      }
      rejectingCleanupExecutor.shutdownNow();
      assertThat(rejectingCleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void successfulCleanupOnDirectExecutorRemovesPolledQueueMeters() throws Exception {
    ExecutorService directCleanupExecutor = mock(ExecutorService.class);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return null;
            })
        .when(directCleanupExecutor)
        .execute(any(Runnable.class));
    ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost").build();
    DefaultRegistry registry = new DefaultRegistry();
    KubernetesStreamingCachingAgentExecution execution =
        new KubernetesStreamingCachingAgentExecution(
            namedAccountCredentials,
            providerCache,
            List.of(),
            registry,
            new NoOpStartupConcurrencyControl(),
            directCleanupExecutor) {
          @Override
          protected ApiClient createApiClient() {
            return apiClient;
          }

          @Override
          Set<APIResource> loadKubernetesResources(ApiClient client) {
            return Set.of();
          }

          @Override
          protected KubernetesStreamingWatcherFactory createWatcherFactory(
              ApiClient client, ExecutorService executorService) {
            return new AlwaysHealthyWatcherFactory(client, executorService);
          }
        };
    Future<?> executionFuture = agentExecutor.submit(() -> execution.executeAgent(agent));
    try {
      awaitStatePublished(execution);

      execution.stopExecutingAndCleanup().get(5, TimeUnit.SECONDS);

      executionFuture.get(5, TimeUnit.SECONDS);
      assertPolledQueueMetersRemoved(registry);
      org.mockito.Mockito.verify(directCleanupExecutor).execute(any(Runnable.class));
    } finally {
      if (!executionFuture.isDone()) {
        currentState(execution).stopAndWait(0);
        executionFuture.get(5, TimeUnit.SECONDS);
      }
    }
  }

  private KubernetesStreamingCachingAgentExecution createExecution(
      Supplier<ApiClient> apiClientSupplier) {
    return new KubernetesStreamingCachingAgentExecution(
        namedAccountCredentials,
        providerCache,
        List.of(),
        new NoopRegistry(),
        new NoOpStartupConcurrencyControl(),
        cleanupExecutor) {
      @Override
      protected ApiClient createApiClient() {
        return apiClientSupplier.get();
      }
    };
  }

  private KubernetesStreamingCachingAgentExecution createFailingStartupExecution(
      ApiClient apiClient,
      DefaultRegistry registry,
      RuntimeException startupFailure,
      RuntimeException cleanupFailure,
      boolean interruptDuringCleanup,
      AtomicReference<ExecutorService> internalExecutor,
      AtomicReference<PartiallyFailingWatcherFactory> watcherFactory) {
    return new KubernetesStreamingCachingAgentExecution(
        namedAccountCredentials,
        providerCache,
        List.of(),
        registry,
        new NoOpStartupConcurrencyControl(),
        cleanupExecutor) {
      @Override
      protected ApiClient createApiClient() {
        return apiClient;
      }

      @Override
      Set<APIResource> loadKubernetesResources(ApiClient client) {
        return Set.of();
      }

      @Override
      protected KubernetesStreamingWatcherFactory createWatcherFactory(
          ApiClient client, ExecutorService executorService) {
        internalExecutor.set(executorService);
        PartiallyFailingWatcherFactory factory =
            new PartiallyFailingWatcherFactory(
                client, executorService, startupFailure, cleanupFailure, interruptDuringCleanup);
        watcherFactory.set(factory);
        return factory;
      }
    };
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for test latch");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  private static void awaitThreadState(
      AtomicReference<Thread> threadReference, Thread.State expectedState)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      Thread thread = threadReference.get();
      if (thread != null && thread.getState() == expectedState) {
        return;
      }
      Thread.sleep(10);
    }
    throw new AssertionError("Timed out waiting for thread state " + expectedState);
  }

  private static void awaitStatePublished(KubernetesStreamingCachingAgentExecution execution)
      throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      if (currentState(execution) != null) {
        return;
      }
      Thread.sleep(10);
    }
    throw new AssertionError("Timed out waiting for execution state publication");
  }

  private static void assertStartupFailure(Future<?> execution, String expectedMessage) {
    assertThatThrownBy(() -> execution.get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(expectedMessage);
  }

  private static void awaitCleanup(CompletableFuture<Void> cleanup) {
    if (cleanup == null) {
      return;
    }
    try {
      cleanup.get(5, TimeUnit.SECONDS);
    } catch (Exception ignored) {
      // The assertions report cleanup failures; this only prevents test thread leaks.
    }
  }

  private static void assertPolledQueueMetersRemoved(DefaultRegistry registry) {
    PolledMeter.update(registry);
    assertThat(registry.gauge(queueMetricId(registry, "queueSize", "events")).value()).isNaN();
    assertThat(registry.gauge(queueMetricId(registry, "queueSize", "bulkedEvents")).value())
        .isNaN();
    assertThat(registry.gauge(queueMetricId(registry, "queueRemainingCapacity", "events")).value())
        .isNaN();
    assertThat(
            registry
                .gauge(queueMetricId(registry, "queueRemainingCapacity", "bulkedEvents"))
                .value())
        .isNaN();
  }

  private static Id queueMetricId(DefaultRegistry registry, String metric, String queueType) {
    return registry
        .createId("kubernetes.agent.streaming.execution." + metric)
        .withTag("account", "test-account")
        .withTag("queueType", queueType);
  }

  private static void forceStop(
      PartiallyFailingWatcherFactory watcherFactory, ExecutorService executorService)
      throws InterruptedException {
    if (watcherFactory != null) {
      watcherFactory.forceStop();
    }
    if (executorService != null) {
      executorService.shutdownNow();
      assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  private static final class PartiallyFailingWatcherFactory
      extends KubernetesStreamingWatcherFactory {
    private final ExecutorService executorService;
    private final RuntimeException startupFailure;
    private final RuntimeException cleanupFailure;
    private final boolean interruptDuringCleanup;
    private final CountDownLatch watcherStarted = new CountDownLatch(1);
    private final CountDownLatch watcherStopped = new CountDownLatch(1);
    private Future<?> watcherFuture;

    private PartiallyFailingWatcherFactory(
        ApiClient client,
        ExecutorService executorService,
        RuntimeException startupFailure,
        RuntimeException cleanupFailure,
        boolean interruptDuringCleanup) {
      super(client, "test-account", 200, executorService, new NoOpStartupConcurrencyControl());
      this.executorService = executorService;
      this.startupFailure = startupFailure;
      this.cleanupFailure = cleanupFailure;
      this.interruptDuringCleanup = interruptDuringCleanup;
    }

    @Override
    public void startAllWatchers() {
      awaitQueueWorkersStarted();
      watcherFuture =
          executorService.submit(
              () -> {
                watcherStarted.countDown();
                try {
                  new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  watcherStopped.countDown();
                }
              });
      await(watcherStarted);
      throw startupFailure;
    }

    @Override
    public void stopAllRegisteredWatchers() {
      if (watcherFuture != null) {
        watcherFuture.cancel(true);
      }
      if (interruptDuringCleanup) {
        Thread.currentThread().interrupt();
      }
      if (cleanupFailure != null) {
        throw cleanupFailure;
      }
    }

    private void awaitQueueWorkersStarted() {
      ThreadPoolExecutor executor = (ThreadPoolExecutor) executorService;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (System.nanoTime() < deadline) {
        if (executor.getActiveCount() >= 2) {
          return;
        }
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }
      throw new AssertionError("Queue workers did not start");
    }

    private void forceStop() {
      if (watcherFuture != null) {
        watcherFuture.cancel(true);
      }
    }
  }

  private static final class AlwaysHealthyWatcherFactory extends KubernetesStreamingWatcherFactory {

    private AlwaysHealthyWatcherFactory(ApiClient client, ExecutorService executorService) {
      super(client, "test-account", 200, executorService, new NoOpStartupConcurrencyControl());
    }

    @Override
    boolean allWatchersHealthy(long livenessTimeoutMillis) {
      return true;
    }
  }

  private static final class BlockingCleanupWatcherFactory
      extends KubernetesStreamingWatcherFactory {
    private final AtomicInteger stopCount = new AtomicInteger();
    private final CountDownLatch cleanupStarted = new CountDownLatch(1);
    private final CountDownLatch allowCleanup = new CountDownLatch(1);

    private BlockingCleanupWatcherFactory(ApiClient client, ExecutorService executorService) {
      this(client, executorService, null);
    }

    private BlockingCleanupWatcherFactory(
        ApiClient client, ExecutorService executorService, RuntimeException cleanupFailure) {
      super(client, "test-account", 200, executorService, new NoOpStartupConcurrencyControl());
      this.cleanupFailure = cleanupFailure;
    }

    private final RuntimeException cleanupFailure;

    @Override
    public void stopAllRegisteredWatchers() {
      stopCount.incrementAndGet();
      cleanupStarted.countDown();
      await(allowCleanup);
      if (cleanupFailure != null) {
        throw cleanupFailure;
      }
    }

    @Override
    boolean allWatchersHealthy(long livenessTimeoutMillis) {
      return true;
    }
  }

  private static final class RejectOnceExecutorService extends AbstractExecutorService {
    private final ExecutorService delegate = Executors.newSingleThreadExecutor();
    private final AtomicInteger submissions = new AtomicInteger();

    @Override
    public void shutdown() {
      delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
      return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
      return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
      return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
      if (submissions.getAndIncrement() == 0) {
        throw new RejectedExecutionException("expected cleanup rejection");
      }
      command.run();
    }
  }

  private static State currentState(KubernetesStreamingCachingAgentExecution execution)
      throws ReflectiveOperationException {
    java.lang.reflect.Field lifecycleField =
        KubernetesStreamingCachingAgentExecution.class.getDeclaredField("lifecycle");
    lifecycleField.setAccessible(true);
    AtomicReference<?> lifecycle = (AtomicReference<?>) lifecycleField.get(execution);
    Object snapshot = lifecycle.get();
    java.lang.reflect.Field stateField = snapshot.getClass().getDeclaredField("state");
    stateField.setAccessible(true);
    return (State) stateField.get(snapshot);
  }
}
