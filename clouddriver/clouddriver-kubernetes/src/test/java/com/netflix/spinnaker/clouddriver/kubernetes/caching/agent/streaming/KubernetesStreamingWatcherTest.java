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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.gson.JsonParser;
import com.netflix.spinnaker.cats.agent.NoOpStartupConcurrencyControl;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesListObject;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class KubernetesStreamingWatcherTest {

  private ExecutorService executor;
  private State state;
  private BlockingQueue<KubernetesStreamingEvent> eventQueue;
  private K8SListWatchAdapter adapter;
  private Set<Keys.InfrastructureCacheKey> knownKeys;
  private Supplier<Boolean> isRunning;

  @BeforeEach
  void setUp() {
    knownKeys = new HashSet<>();
    executor = Executors.newSingleThreadExecutor();
    ApiClient k8sClient = mock(ApiClient.class);
    adapter = mock(K8SListWatchAdapter.class);
    Mockito.when(k8sClient.getReadTimeout()).thenReturn(0);
    state =
        new State(
            "test-account",
            executor,
            new KubernetesStreamingWatcherFactory(
                k8sClient, "account", 0, executor, new NoOpStartupConcurrencyControl()),
            new OkHttpClient());
    eventQueue = new ArrayBlockingQueue<>(100);
    isRunning = mock(Supplier.class);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    boolean junitThreadInterrupted = Thread.interrupted();
    executor.shutdownNow();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    assertThat(junitThreadInterrupted).isFalse();
  }

  @Test
  void testProcessErrorObject() throws ApiException, IOException {
    KubernetesStreamingWatcher.Sleeper sleeper = mock(KubernetesStreamingWatcher.Sleeper.class);
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", eventQueue, sleeper);
    when(isRunning.get())
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(false);

    DynamicKubernetesListObject emptyList = getList(getJsonFixture("kubeapi/pods.default.json"));
    when(adapter.list(anyInt(), any(), eq(0), isNull())).thenReturn(emptyList);

    Watchable<DynamicKubernetesObject> watchable = mock(Watchable.class);
    when(watchable.hasNext()).thenReturn(true);
    V1Status status = new V1Status();
    status.setCode(HttpURLConnection.HTTP_GONE);
    Watch.Response<DynamicKubernetesObject> response = new Watch.Response<>("ERROR", status);
    when(watchable.next()).thenReturn(response);
    when(adapter.watch(any(), any())).thenReturn(watchable);

    watcher.run();
    assertThat(eventQueue.size()).isEqualTo(0);

    verify(adapter).list(20, "0", 0, null);
    verify(adapter).list(20, "", 0, null);
    verifyNoInteractions(sleeper);
  }

  @Test
  void testProcessNullMeta() throws ApiException, IOException {
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1");
    when(isRunning.get()).thenReturn(true).thenReturn(true).thenReturn(false);

    DynamicKubernetesListObject nullMetaList = getList(getJsonFixture("initialList/nullMeta.json"));
    when(adapter.list(anyInt(), any(), eq(0), isNull())).thenReturn(nullMetaList);

    Watchable<DynamicKubernetesObject> watchable = mock(Watchable.class);
    when(watchable.hasNext()).thenReturn(false);
    when(adapter.watch(any(), any())).thenReturn(watchable);

    watcher.run();
    assertThat(eventQueue.size()).isEqualTo(0);
    assertThat(state.getLastReceivedEventTime()).isEqualTo(0L);
    assertThat(state.getLastProcessedEventBatchTime()).isEqualTo(0L);
  }

  @Test
  void testProcessesListAndEvents() throws ApiException, IOException {
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1");
    when(isRunning.get()).thenReturn(false);

    DynamicKubernetesListObject nullMetaList = getList(getJsonFixture("initialList/pods.json"));
    when(adapter.list(anyInt(), any(), eq(0), isNull())).thenReturn(nullMetaList);

    Watchable<DynamicKubernetesObject> watchable = mock(Watchable.class);
    when(watchable.hasNext()).thenReturn(true);
    DynamicKubernetesObject obj = getObject(getJsonFixture("delete/pod.json"));
    Watch.Response<DynamicKubernetesObject> response = new Watch.Response<>("DELETED", obj);
    when(watchable.next()).thenReturn(response);
    when(adapter.watch(any(), any())).thenReturn(watchable);

    watcher.run();
    assertThat(eventQueue.size()).isEqualTo(0);
    assertThat(state.getLastReceivedEventTime()).isEqualTo(0L);
    assertThat(state.getLastProcessedEventBatchTime()).isEqualTo(0L);
  }

  @Test
  void testInterruptedIOExceptionCausesGracefulExit() throws Exception {
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1");
    when(isRunning.get()).thenReturn(true);
    InterruptedIOException interruptedIOException = new InterruptedIOException("IO interrupted");
    when(adapter.list(anyInt(), any(), eq(0), isNull()))
        .thenThrow(new RuntimeException("Wrapper exception", interruptedIOException));

    Future<?> execution =
        executor.submit(
            () -> {
              assertThat(Thread.currentThread().isInterrupted()).isFalse();
              try {
                watcher.run();
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
              } finally {
                Thread.interrupted();
              }
            });

    execution.get(5, TimeUnit.SECONDS);
    assertWorkerInterruptCleared();
    assertThat(eventQueue).isEmpty();
  }

  @Test
  void testAlreadyInterruptedThreadExitsAndPreservesInterrupt() throws Exception {
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1");
    when(isRunning.get()).thenReturn(true);

    Future<?> execution =
        executor.submit(
            () -> {
              assertThat(Thread.currentThread().isInterrupted()).isFalse();
              try {
                Thread.currentThread().interrupt();
                watcher.run();
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
              } finally {
                Thread.interrupted();
              }
            });

    execution.get(5, TimeUnit.SECONDS);
    assertWorkerInterruptCleared();
    verifyNoInteractions(adapter);
  }

  @Test
  void testWatchOpenSocketTimeoutIsPacedWithoutRelistingOrInterrupting() throws Exception {
    KubernetesStreamingWatcher.Sleeper sleeper = mock(KubernetesStreamingWatcher.Sleeper.class);
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", eventQueue, sleeper);
    when(isRunning.get()).thenReturn(true, true, true, true, false);

    DynamicKubernetesListObject emptyList =
        getList(
            "{\"apiVersion\":\"v1\",\"kind\":\"PodList\","
                + "\"metadata\":{\"resourceVersion\":\"123\"},\"items\":[]}");
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList);

    Watchable<DynamicKubernetesObject> secondWatch = mock(Watchable.class);
    when(secondWatch.hasNext()).thenReturn(false);
    when(adapter.watch(60 * 5, "123")).thenThrow(wrappedSocketTimeout()).thenReturn(secondWatch);

    boolean interrupted;
    try {
      watcher.run();
    } finally {
      interrupted = Thread.interrupted();
    }

    verify(adapter, times(1)).list(20, "0", 0, null);
    verify(adapter, times(2)).watch(60 * 5, "123");
    verify(secondWatch).close();
    assertThat(interrupted).isFalse();
    assertThat(watcher.getLastHeartbeatTimeMillis()).isPositive();
    verify(sleeper, times(1)).sleep(1000);
  }

  @Test
  void testInterruptedWatchOpenDelayPreservesInterruptAndExits() throws Exception {
    KubernetesStreamingWatcher.Sleeper sleeper = mock(KubernetesStreamingWatcher.Sleeper.class);
    doThrow(new InterruptedException("watch open retry interrupted")).when(sleeper).sleep(1000);
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", eventQueue, sleeper);
    when(isRunning.get()).thenReturn(true, true);
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList("123"));
    when(adapter.watch(60 * 5, "123")).thenThrow(wrappedSocketTimeout());

    Future<?> execution =
        executor.submit(
            () -> {
              assertThat(Thread.currentThread().isInterrupted()).isFalse();
              try {
                watcher.run();
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
              } finally {
                Thread.interrupted();
              }
            });

    execution.get(5, TimeUnit.SECONDS);
    assertWorkerInterruptCleared();
    verify(sleeper, times(1)).sleep(1000);
    verify(adapter, times(1)).list(20, "0", 0, null);
    verify(adapter, times(1)).watch(60 * 5, "123");
  }

  @Test
  void testSocketTimeoutRetriesListWithoutInterrupting() throws Exception {
    KubernetesStreamingWatcher.Sleeper sleeper = mock(KubernetesStreamingWatcher.Sleeper.class);
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", eventQueue, sleeper);
    when(isRunning.get()).thenReturn(true, true, true, true, false);

    DynamicKubernetesListObject emptyList =
        getList(
            "{\"apiVersion\":\"v1\",\"kind\":\"PodList\","
                + "\"metadata\":{\"resourceVersion\":\"123\"},\"items\":[]}");
    when(adapter.list(20, "0", 0, null))
        .thenThrow(new RuntimeException("wrapper", new SocketTimeoutException("read timed out")))
        .thenReturn(emptyList);

    Watchable<DynamicKubernetesObject> watch = mock(Watchable.class);
    when(watch.hasNext()).thenReturn(false);
    when(adapter.watch(60 * 5, "123")).thenReturn(watch);

    boolean interrupted;
    try {
      watcher.run();
    } finally {
      interrupted = Thread.interrupted();
    }

    verify(adapter, times(2)).list(20, "0", 0, null);
    verify(adapter).watch(60 * 5, "123");
    verify(watch).close();
    assertThat(interrupted).isFalse();
    verify(sleeper).sleep(1000);
  }

  @Test
  void testNonGoneApiExceptionRetryIsPaced() throws Exception {
    KubernetesStreamingWatcher.Sleeper sleeper = mock(KubernetesStreamingWatcher.Sleeper.class);
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", eventQueue, sleeper);
    when(isRunning.get()).thenReturn(true, true, false);
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList("123"));
    ApiException failure = mock(ApiException.class);
    when(failure.getCode()).thenReturn(HttpURLConnection.HTTP_INTERNAL_ERROR);
    when(adapter.watch(60 * 5, "123")).thenThrow(failure);

    watcher.run();

    verify(sleeper).sleep(1000);
    verify(adapter, times(1)).list(20, "0", 0, null);
  }

  @Test
  void testGenericRuntimeFailureRetryIsPaced() throws Exception {
    KubernetesStreamingWatcher.Sleeper sleeper = mock(KubernetesStreamingWatcher.Sleeper.class);
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", eventQueue, sleeper);
    when(isRunning.get()).thenReturn(true, true, false);
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList("123"));
    when(adapter.watch(60 * 5, "123")).thenThrow(new IllegalStateException("watch failed"));

    watcher.run();

    verify(sleeper).sleep(1000);
    verify(adapter, times(1)).list(20, "0", 0, null);
  }

  @Test
  void testConnectExceptionRetryIsPacedOnce() throws Exception {
    KubernetesStreamingWatcher.Sleeper sleeper = mock(KubernetesStreamingWatcher.Sleeper.class);
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", eventQueue, sleeper);
    when(isRunning.get()).thenReturn(true, true, false);
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList("123"));
    ApiException failure = mock(ApiException.class);
    when(failure.getCause()).thenReturn(new java.net.ConnectException("connection failed"));
    when(adapter.watch(60 * 5, "123")).thenThrow(failure);

    watcher.run();

    verify(sleeper, times(1)).sleep(1000);
  }

  @Test
  void testNonGoneErrorResponseRetryIsPaced() throws Exception {
    KubernetesStreamingWatcher.Sleeper sleeper = mock(KubernetesStreamingWatcher.Sleeper.class);
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", eventQueue, sleeper);
    when(isRunning.get()).thenReturn(true, true, true, false);
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList("123"));

    V1Status status = new V1Status().code(HttpURLConnection.HTTP_INTERNAL_ERROR);
    Watchable<DynamicKubernetesObject> watch = mock(Watchable.class);
    when(watch.hasNext()).thenReturn(true);
    when(watch.next()).thenReturn(new Watch.Response<>("ERROR", status));
    when(adapter.watch(60 * 5, "123")).thenReturn(watch);

    watcher.run();

    verify(sleeper).sleep(1000);
    verify(watch).close();
  }

  @Test
  void testInterruptedRetrySleepPreservesInterruptAndExits() throws Exception {
    KubernetesStreamingWatcher.Sleeper sleeper = mock(KubernetesStreamingWatcher.Sleeper.class);
    doThrow(new InterruptedException("retry interrupted")).when(sleeper).sleep(1000);
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", eventQueue, sleeper);
    when(isRunning.get()).thenReturn(true, true);
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList("123"));
    ApiException failure = mock(ApiException.class);
    when(failure.getCause()).thenReturn(new java.net.ConnectException("connection failed"));
    when(adapter.watch(60 * 5, "123")).thenThrow(failure);

    Future<?> execution =
        executor.submit(
            () -> {
              assertThat(Thread.currentThread().isInterrupted()).isFalse();
              try {
                watcher.run();
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
              } finally {
                Thread.interrupted();
              }
            });

    execution.get(5, TimeUnit.SECONDS);
    assertWorkerInterruptCleared();
    verify(sleeper, times(1)).sleep(1000);
    verify(adapter, times(1)).list(20, "0", 0, null);
    verify(adapter, times(1)).watch(60 * 5, "123");
  }

  @Test
  void testRecordsHeartbeatUsingInjectedClock() throws ApiException {
    long heartbeatTime = 123_456L;
    KubernetesStreamingWatcher watcher =
        createWatcher("Pod", "", "v1", 0, eventQueue, millis -> {}, () -> heartbeatTime);
    when(isRunning.get()).thenReturn(true, false);
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList("123"));

    watcher.run();

    assertThat(watcher.getLastHeartbeatTimeMillis()).isEqualTo(heartbeatTime);
  }

  @Test
  void productionConstructorRecordsHeartbeatFromMonotonicTicker() throws ApiException {
    KubernetesStreamingWatcher watcher =
        new KubernetesStreamingWatcher(
            adapter,
            state,
            "Pod",
            "",
            "v1",
            "account",
            0,
            eventQueue,
            knownKeys,
            1_000,
            20,
            300,
            new NoOpStartupConcurrencyControl(),
            isRunning);
    when(isRunning.get()).thenReturn(true, false);
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList("123"));

    long before = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    watcher.run();
    long after = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());

    assertThat(watcher.getLastHeartbeatTimeMillis()).isBetween(before, after);
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, 0})
  void zeroAndNegativeTickerValuesAreRecordedHeartbeats(long heartbeatTime) throws ApiException {
    KubernetesStreamingWatcher watcher =
        createWatcher("Pod", "", "v1", 0, eventQueue, millis -> {}, () -> heartbeatTime);
    when(isRunning.get()).thenReturn(true, false);
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList("123"));

    watcher.run();

    assertThat(watcher.hasRecordedHeartbeat()).isTrue();
    assertThat(watcher.getLastHeartbeatTimeMillis()).isEqualTo(heartbeatTime);
  }

  @Test
  void testRecordsHeartbeatAfterEveryPaginatedListResponse() throws ApiException {
    KubernetesStreamingWatcher.Sleeper sleeper = mock(KubernetesStreamingWatcher.Sleeper.class);
    LongSupplier currentTimeMillis = mock(LongSupplier.class);
    KubernetesStreamingWatcher watcher =
        createWatcher("Pod", "", "v1", 1, eventQueue, sleeper, currentTimeMillis);
    when(isRunning.get()).thenReturn(true, false);
    when(adapter.list(20, "0", 1, null)).thenReturn(paginatedEmptyList("123", "next"));
    when(adapter.list(20, "0", 1, "next")).thenReturn(paginatedEmptyList("124", ""));
    when(currentTimeMillis.getAsLong()).thenReturn(101L, 102L);

    watcher.run();

    assertThat(watcher.getLastHeartbeatTimeMillis()).isEqualTo(102L);
    verify(currentTimeMillis, times(2)).getAsLong();
  }

  @Test
  void testRecordsHeartbeatForBookmarkAndErrorResponses() throws ApiException, IOException {
    AtomicLong currentTimeMillis = new AtomicLong();
    KubernetesStreamingWatcher watcher =
        createWatcher(
            "Pod", "", "v1", 0, eventQueue, millis -> {}, currentTimeMillis::incrementAndGet);
    when(isRunning.get()).thenReturn(true, true, true, true, false);

    DynamicKubernetesListObject emptyList =
        getList(
            "{\"apiVersion\":\"v1\",\"kind\":\"PodList\","
                + "\"metadata\":{\"resourceVersion\":\"123\"},\"items\":[]}");
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList);

    DynamicKubernetesObject bookmarkObject = getObject(getJsonFixture("delete/pod.json"));
    Watch.Response<DynamicKubernetesObject> bookmark =
        new Watch.Response<>("BOOKMARK", bookmarkObject);
    V1Status errorStatus = new V1Status().code(HttpURLConnection.HTTP_INTERNAL_ERROR);
    Watch.Response<DynamicKubernetesObject> error = new Watch.Response<>("ERROR", errorStatus);
    Watchable<DynamicKubernetesObject> watch = mock(Watchable.class);
    when(watch.hasNext()).thenReturn(true, true);
    when(watch.next()).thenReturn(bookmark, error);
    when(adapter.watch(60 * 5, "123")).thenReturn(watch);

    watcher.run();

    assertThat(watcher.getLastHeartbeatTimeMillis()).isEqualTo(4L);
    verify(watch).close();
  }

  @Test
  void testActiveWatchTimeoutReconnectsFromLatestResourceVersion() throws Exception {
    KubernetesStreamingWatcher.Sleeper sleeper = mock(KubernetesStreamingWatcher.Sleeper.class);
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", eventQueue, sleeper);
    when(isRunning.get()).thenReturn(true, true, true, true, true, true, false);
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList("123"));

    DynamicKubernetesObject pod =
        getObject(
            "{\"metadata\":{\"resourceVersion\":\"124\",\"name\":\"pod\","
                + "\"namespace\":\"default\"}}");
    Watchable<DynamicKubernetesObject> firstWatch = mock(Watchable.class);
    when(firstWatch.hasNext()).thenReturn(true).thenThrow(wrappedSocketTimeout());
    when(firstWatch.next()).thenReturn(new Watch.Response<>("ADDED", pod));
    when(adapter.watch(60 * 5, "123")).thenReturn(firstWatch);

    Watchable<DynamicKubernetesObject> secondWatch = mock(Watchable.class);
    when(secondWatch.hasNext()).thenReturn(false);
    when(adapter.watch(60 * 5, "124")).thenReturn(secondWatch);

    watcher.run();

    verify(adapter, times(1)).list(20, "0", 0, null);
    verify(adapter).watch(60 * 5, "123");
    verify(adapter).watch(60 * 5, "124");
    verify(firstWatch).close();
    verify(secondWatch).close();
    verifyNoInteractions(sleeper);
  }

  @Test
  void testInitialDeleteUpdatesReceivedEventTimeAfterEnqueue()
      throws ApiException, InterruptedException {
    BlockingQueue<KubernetesStreamingEvent> assertingQueue = queueThatAssertsTimestampNotUpdated();
    Keys.InfrastructureCacheKey knownKey = mock(Keys.InfrastructureCacheKey.class);
    when(knownKey.getNamespace()).thenReturn("default");
    when(knownKey.getName()).thenReturn("deleted-pod");
    knownKeys.add(knownKey);
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", assertingQueue);
    when(isRunning.get()).thenReturn(true, false);
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList("123"));

    watcher.run();

    verify(assertingQueue)
        .put(argThat(event -> event.getType() == KubernetesStreamingEvent.Type.DELETE));
    assertThat(state.getLastReceivedEventTime()).isPositive();
  }

  @Test
  void testInitialUpsertUpdatesReceivedEventTimeAfterEnqueue()
      throws ApiException, IOException, InterruptedException {
    BlockingQueue<KubernetesStreamingEvent> assertingQueue = queueThatAssertsTimestampNotUpdated();
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", assertingQueue);
    when(isRunning.get()).thenReturn(true, false);
    when(adapter.list(20, "0", 0, null))
        .thenReturn(listWithObject("123", getJsonFixture("delete/pod.json")));

    watcher.run();

    verify(assertingQueue)
        .put(argThat(event -> event.getType() == KubernetesStreamingEvent.Type.UPSERT));
    assertThat(state.getLastReceivedEventTime()).isPositive();
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADDED", "MODIFIED", "DELETED"})
  void testWatchEventUpdatesReceivedEventTimeAfterEnqueue(String watchEventType)
      throws ApiException, IOException, InterruptedException {
    BlockingQueue<KubernetesStreamingEvent> assertingQueue = queueThatAssertsTimestampNotUpdated();
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", assertingQueue);
    when(isRunning.get()).thenReturn(true, true, true, true, false);
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList("123"));

    DynamicKubernetesObject pod = getObject(getJsonFixture("delete/pod.json"));
    Watchable<DynamicKubernetesObject> watch = mock(Watchable.class);
    when(watch.hasNext()).thenReturn(true, false);
    when(watch.next()).thenReturn(new Watch.Response<>(watchEventType, pod));
    when(adapter.watch(60 * 5, "123")).thenReturn(watch);

    watcher.run();

    KubernetesStreamingEvent.Type expectedEventType =
        "DELETED".equals(watchEventType)
            ? KubernetesStreamingEvent.Type.DELETE
            : KubernetesStreamingEvent.Type.UPSERT;
    verify(assertingQueue).put(argThat(event -> event.getType() == expectedEventType));
    assertThat(state.getLastReceivedEventTime()).isPositive();
  }

  @Test
  void testConsumerCannotProcessPublishedEventBeforeEnqueueAccountingCompletes() throws Exception {
    AtomicReference<Future<?>> processing = new AtomicReference<>();
    AtomicReference<Thread> processorThread = new AtomicReference<>();
    AtomicReference<KubernetesStreamingEvent> publishedEvent = new AtomicReference<>();
    BlockingQueue<KubernetesStreamingEvent> racingQueue = mock(BlockingQueue.class);
    doAnswer(
            invocation -> {
              KubernetesStreamingEvent event = invocation.getArgument(0);
              publishedEvent.set(event);
              Future<?> processingFuture =
                  executor.submit(
                      () -> {
                        processorThread.set(Thread.currentThread());
                        state.updateLastProcessedEventBatchTime(List.of(event));
                      });
              processing.set(processingFuture);
              await(
                  () ->
                      processingFuture.isDone()
                          || (processorThread.get() != null
                              && processorThread.get().getState() == Thread.State.WAITING));
              return null;
            })
        .when(racingQueue)
        .put(any());
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", racingQueue);
    when(isRunning.get()).thenReturn(true, false);
    when(adapter.list(20, "0", 0, null))
        .thenReturn(listWithObject("123", getJsonFixture("delete/pod.json")));

    watcher.run();

    processing.get().get(5, TimeUnit.SECONDS);
    assertThatThrownBy(() -> state.updateLastProcessedEventBatchTime(List.of(publishedEvent.get())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unknown");
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(CacheEventPath.class)
  void testInterruptedQueuePutDoesNotUpdateReceivedEventTime(CacheEventPath path) throws Exception {
    BlockingQueue<KubernetesStreamingEvent> failingQueue = mock(BlockingQueue.class);
    doAnswer(
            invocation -> {
              assertThat(state.getLastReceivedEventTime()).isZero();
              throw new InterruptedException("queue put interrupted");
            })
        .when(failingQueue)
        .put(any());
    when(isRunning.get()).thenReturn(true, true, true);
    String podPayload = getJsonFixture("delete/pod.json");
    if (path == CacheEventPath.INITIAL_DELETE) {
      Keys.InfrastructureCacheKey knownKey = mock(Keys.InfrastructureCacheKey.class);
      when(knownKey.getNamespace()).thenReturn("default");
      when(knownKey.getName()).thenReturn("deleted-pod");
      knownKeys.add(knownKey);
    }
    DynamicKubernetesListObject initialList =
        path == CacheEventPath.INITIAL_UPSERT
            ? listWithObject("123", podPayload)
            : emptyList("123");
    when(adapter.list(20, "0", 0, null)).thenReturn(initialList);
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1", failingQueue);

    Watchable<DynamicKubernetesObject> watch = null;
    if (path.isWatchEvent()) {
      DynamicKubernetesObject pod = getObject(podPayload);
      watch = mock(Watchable.class);
      when(watch.hasNext()).thenReturn(true);
      when(watch.next()).thenReturn(new Watch.Response<>(path.watchEventType, pod));
      when(adapter.watch(60 * 5, "123")).thenReturn(watch);
    }

    Future<?> execution =
        executor.submit(
            () -> {
              assertThat(Thread.currentThread().isInterrupted()).isFalse();
              try {
                watcher.run();
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
              } finally {
                Thread.interrupted();
              }
            });

    execution.get(5, TimeUnit.SECONDS);
    assertWorkerInterruptCleared();
    verify(failingQueue).put(argThat(event -> event.getType() == path.expectedEventType));
    if (watch != null) {
      verify(watch).close();
    }
    assertThat(state.getLastReceivedEventTime()).isZero();
  }

  @Test
  void testUnqueuedWatchResponsesDoNotUpdateReceivedEventTime() throws ApiException, IOException {
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1");
    when(isRunning.get()).thenReturn(true, true, true, true, true, true, false);
    when(adapter.list(20, "0", 0, null)).thenReturn(emptyList("123"));

    DynamicKubernetesObject pod = getObject(getJsonFixture("delete/pod.json"));
    DynamicKubernetesObject nullMetadata = getObject("{\"apiVersion\":\"v1\",\"kind\":\"Pod\"}");
    V1Status errorStatus = new V1Status().code(HttpURLConnection.HTTP_INTERNAL_ERROR);
    Watchable<DynamicKubernetesObject> watch = mock(Watchable.class);
    when(watch.hasNext()).thenReturn(true, true, true, true);
    when(watch.next())
        .thenReturn(
            new Watch.Response<>("BOOKMARK", pod),
            new Watch.Response<>("ADDED", nullMetadata),
            new Watch.Response<>("UNSUPPORTED", pod),
            new Watch.Response<>("ERROR", errorStatus));
    when(adapter.watch(60 * 5, "123")).thenReturn(watch);

    watcher.run();

    assertThat(eventQueue).isEmpty();
    assertThat(state.getLastReceivedEventTime()).isZero();
  }

  private String getJsonFixture(String path) throws IOException {
    InputStream is =
        getClass()
            .getResourceAsStream(
                String.format(
                    "/com/netflix/spinnaker/clouddriver/kubernetes/caching/agent/streaming/__files/%s",
                    path));
    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
  }

  private DynamicKubernetesListObject getList(String payload) {
    return new DynamicKubernetesListObject(JsonParser.parseString(payload).getAsJsonObject());
  }

  private DynamicKubernetesObject getObject(String payload) {
    return new DynamicKubernetesObject(JsonParser.parseString(payload).getAsJsonObject());
  }

  private DynamicKubernetesListObject emptyList(String resourceVersion) {
    return getList(
        String.format(
            "{\"apiVersion\":\"v1\",\"kind\":\"PodList\","
                + "\"metadata\":{\"resourceVersion\":\"%s\"},\"items\":[]}",
            resourceVersion));
  }

  private DynamicKubernetesListObject listWithObject(String resourceVersion, String objectPayload) {
    return getList(
        String.format(
            "{\"apiVersion\":\"v1\",\"kind\":\"PodList\","
                + "\"metadata\":{\"resourceVersion\":\"%s\"},\"items\":[%s]}",
            resourceVersion, objectPayload));
  }

  private DynamicKubernetesListObject paginatedEmptyList(
      String resourceVersion, String continuation) {
    return getList(
        String.format(
            "{\"apiVersion\":\"v1\",\"kind\":\"PodList\","
                + "\"metadata\":{\"resourceVersion\":\"%s\",\"continue\":\"%s\"},"
                + "\"items\":[]}",
            resourceVersion, continuation));
  }

  private BlockingQueue<KubernetesStreamingEvent> queueThatAssertsTimestampNotUpdated()
      throws InterruptedException {
    BlockingQueue<KubernetesStreamingEvent> queue = mock(BlockingQueue.class);
    doAnswer(
            invocation -> {
              assertThat(state.getLastReceivedEventTime()).isZero();
              return null;
            })
        .when(queue)
        .put(any());
    return queue;
  }

  private static void await(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(10);
    }
    throw new AssertionError("Timed out waiting for queue processing state");
  }

  private void assertWorkerInterruptCleared() throws Exception {
    Future<Boolean> interrupted = executor.submit(() -> Thread.currentThread().isInterrupted());
    assertThat(interrupted.get(5, TimeUnit.SECONDS)).isFalse();
  }

  private RuntimeException wrappedSocketTimeout() {
    return new RuntimeException(
        "outer", new RuntimeException("inner", new SocketTimeoutException("read timed out")));
  }

  private enum CacheEventPath {
    INITIAL_UPSERT(null, KubernetesStreamingEvent.Type.UPSERT),
    INITIAL_DELETE(null, KubernetesStreamingEvent.Type.DELETE),
    WATCH_ADDED("ADDED", KubernetesStreamingEvent.Type.UPSERT),
    WATCH_MODIFIED("MODIFIED", KubernetesStreamingEvent.Type.UPSERT),
    WATCH_DELETED("DELETED", KubernetesStreamingEvent.Type.DELETE);

    private final String watchEventType;
    private final KubernetesStreamingEvent.Type expectedEventType;

    CacheEventPath(String watchEventType, KubernetesStreamingEvent.Type expectedEventType) {
      this.watchEventType = watchEventType;
      this.expectedEventType = expectedEventType;
    }

    private boolean isWatchEvent() {
      return watchEventType != null;
    }
  }

  private KubernetesStreamingWatcher createWatcher(String kind, String group, String version) {
    return createWatcher(kind, group, version, eventQueue);
  }

  private KubernetesStreamingWatcher createWatcher(
      String kind,
      String group,
      String version,
      BlockingQueue<KubernetesStreamingEvent> watcherEventQueue) {
    return createWatcher(kind, group, version, 0, watcherEventQueue, millis -> {});
  }

  private KubernetesStreamingWatcher createWatcher(
      String kind,
      String group,
      String version,
      BlockingQueue<KubernetesStreamingEvent> watcherEventQueue,
      KubernetesStreamingWatcher.Sleeper sleeper) {
    return createWatcher(kind, group, version, 0, watcherEventQueue, sleeper);
  }

  private KubernetesStreamingWatcher createWatcher(
      String kind,
      String group,
      String version,
      int paginationSize,
      BlockingQueue<KubernetesStreamingEvent> watcherEventQueue,
      KubernetesStreamingWatcher.Sleeper sleeper) {
    return createWatcher(
        kind,
        group,
        version,
        paginationSize,
        watcherEventQueue,
        sleeper,
        System::currentTimeMillis);
  }

  private KubernetesStreamingWatcher createWatcher(
      String kind,
      String group,
      String version,
      int paginationSize,
      BlockingQueue<KubernetesStreamingEvent> watcherEventQueue,
      KubernetesStreamingWatcher.Sleeper sleeper,
      LongSupplier currentTimeMillis) {
    return new KubernetesStreamingWatcher(
        adapter,
        state,
        kind,
        group,
        version,
        "account",
        paginationSize,
        watcherEventQueue,
        knownKeys,
        1000,
        20,
        60 * 5,
        new NoOpStartupConcurrencyControl(),
        isRunning,
        sleeper,
        currentTimeMillis);
  }
}
