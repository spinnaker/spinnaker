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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.netflix.spinnaker.cats.agent.LongRunningAgentExecutionState;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

class KubernetesStreamingCachingAgentStateTest {

  private static final long START_TIME_MILLIS = 10_000;
  private static final long LIVENESS_TIMEOUT_MILLIS = 1_000;

  private ExecutorService executor;
  private KubernetesStreamingWatcherFactory kubernetesWatcherFactory;
  private AtomicLong currentTimeMillis;
  private CompletableFuture<Void> batcherFuture;
  private CompletableFuture<Void> processorFuture;
  private State state;
  private Logger stateLogger;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    executor = mock(ExecutorService.class);
    kubernetesWatcherFactory = mock(KubernetesStreamingWatcherFactory.class);
    when(kubernetesWatcherFactory.allWatchersHealthy(anyLong())).thenReturn(true);
    currentTimeMillis = new AtomicLong(START_TIME_MILLIS);
    batcherFuture = new CompletableFuture<>();
    processorFuture = new CompletableFuture<>();
    state = newState(currentTimeMillis::get);
    state.monitorWorkers(batcherFuture, processorFuture);
    state.start();

    stateLogger = (Logger) LoggerFactory.getLogger(State.class);
    logAppender = new ListAppender<>();
    logAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    stateLogger.addAppender(logAppender);
    logAppender.start();
  }

  @AfterEach
  void tearDown() {
    stateLogger.detachAppender(logAppender);
    logAppender.stop();
  }

  @Test
  void startTwiceIsRejected() {
    assertThatThrownBy(state::start).hasMessageContaining("Already started");
  }

  @Test
  void quietAgentRemainsRunningLongAfterReadiness() {
    currentTimeMillis.set(START_TIME_MILLIS + 1_000_000);

    assertThat(state.getState(100, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);
    verify(kubernetesWatcherFactory).allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS);
  }

  @Test
  void unhealthyWatcherFactoryFailsStateWithWatcherDiagnostic() {
    when(kubernetesWatcherFactory.allWatchersHealthy(anyLong())).thenReturn(false);

    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);

    assertThat(warningMessages()).anyMatch(message -> message.contains("watcher"));
  }

  @Test
  void unmonitoredBatcherFailsStateWithBatcherDiagnostic() {
    state = startedStateWithoutWorkers();

    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);

    assertThat(warningMessages())
        .anyMatch(message -> message.contains("batcher") && message.contains("not monitored"));
  }

  @Test
  void unmonitoredProcessorFailsStateWithProcessorDiagnostic() {
    state = startedStateWithoutWorkers();
    state.monitorWorkers(new CompletableFuture<>(), null);

    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);

    assertThat(warningMessages())
        .anyMatch(message -> message.contains("processor") && message.contains("not monitored"));
  }

  @ParameterizedTest
  @EnumSource(WorkerCompletion.class)
  void completedBatcherFailsStateWithBatcherDiagnostic(WorkerCompletion completion) {
    state.monitorWorkers(completion.future(), new CompletableFuture<>());

    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);

    assertThat(warningMessages())
        .anyMatch(
            message -> message.contains("batcher") && message.contains(completion.logDescription));
  }

  @ParameterizedTest
  @EnumSource(WorkerCompletion.class)
  void completedProcessorFailsStateWithProcessorDiagnostic(WorkerCompletion completion) {
    state.monitorWorkers(new CompletableFuture<>(), completion.future());

    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);

    assertThat(warningMessages())
        .anyMatch(
            message ->
                message.contains("processor") && message.contains(completion.logDescription));
  }

  @Test
  void enqueuedEventFailsExactlyAtTimeoutAndRecoversAfterProcessing() throws Exception {
    KubernetesStreamingEvent event = enqueueAt(20_000);

    currentTimeMillis.set(20_999);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);

    currentTimeMillis.set(21_000);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);
    assertThat(warningMessages())
        .anyMatch(
            message ->
                message.contains("backlog")
                    && message.contains("1000 ms")
                    && message.contains("limit 1000 ms"));

    state.updateLastProcessedEventBatchTime(List.of(event));
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);
  }

  @Test
  void eventEnqueuedAtTimeZeroFailsExactlyAtTimeoutAndRecoversAfterProcessing() throws Exception {
    currentTimeMillis.set(0);
    state = newState(currentTimeMillis::get);
    state.monitorWorkers(batcherFuture, processorFuture);
    state.start();
    KubernetesStreamingEvent event = enqueueAt(0);

    currentTimeMillis.set(LIVENESS_TIMEOUT_MILLIS - 1);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);

    currentTimeMillis.set(LIVENESS_TIMEOUT_MILLIS);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);

    state.updateLastProcessedEventBatchTime(List.of(event));
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);
  }

  @Test
  void sustainedBacklogRemainsHealthyWhenEveryRemainingEventIsYoung() throws Exception {
    KubernetesStreamingEvent first = enqueueAt(20_000);
    KubernetesStreamingEvent second = enqueueAt(20_500);

    currentTimeMillis.set(21_000);
    state.updateLastProcessedEventBatchTime(List.of(first));
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);

    KubernetesStreamingEvent third = enqueueAt(21_100);
    currentTimeMillis.set(21_500);
    state.updateLastProcessedEventBatchTime(List.of(second));
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);

    currentTimeMillis.set(22_099);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);
    currentTimeMillis.set(22_100);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);

    state.updateLastProcessedEventBatchTime(List.of(third));
    assertThatThrownBy(() -> state.updateLastProcessedEventBatchTime(List.of(third)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void duplicateOldestTimestampsRemainUntilEveryEventAtThatTimeIsProcessed() throws Exception {
    KubernetesStreamingEvent firstOldest = enqueueAt(20_000);
    KubernetesStreamingEvent secondOldest = enqueueAt(20_000);
    KubernetesStreamingEvent later = enqueueAt(20_500);

    state.updateLastProcessedEventBatchTime(List.of(firstOldest));
    currentTimeMillis.set(20_999);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);
    currentTimeMillis.set(21_000);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);

    state.updateLastProcessedEventBatchTime(List.of(secondOldest));
    currentTimeMillis.set(21_499);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);
    currentTimeMillis.set(21_500);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);

    state.updateLastProcessedEventBatchTime(List.of(later));
  }

  @Test
  void outOfOrderBatchesTrackTheExactRemainingOldestEvent() throws Exception {
    KubernetesStreamingEvent first = enqueueAt(20_000);
    KubernetesStreamingEvent second = enqueueAt(20_500);

    currentTimeMillis.set(20_999);
    state.updateLastProcessedEventBatchTime(List.of(second));
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);

    currentTimeMillis.set(21_000);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);
    state.updateLastProcessedEventBatchTime(List.of(first));
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);
  }

  @Test
  void emptyUnknownDuplicateAndDoubleProcessedBatchesAreRejectedAtomically() throws Exception {
    KubernetesStreamingEvent outstanding = enqueueAt(20_000);
    KubernetesStreamingEvent unknown = mock(KubernetesStreamingEvent.class);

    assertThatThrownBy(() -> state.updateLastProcessedEventBatchTime(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
    assertThatThrownBy(
            () -> state.updateLastProcessedEventBatchTime(List.of(outstanding, outstanding)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("duplicate");
    assertThatThrownBy(() -> state.updateLastProcessedEventBatchTime(List.of(outstanding, unknown)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unknown");

    currentTimeMillis.set(21_000);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);

    state.updateLastProcessedEventBatchTime(List.of(outstanding));
    assertThatThrownBy(() -> state.updateLastProcessedEventBatchTime(List.of(outstanding)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void equalEventsAreAccountedByIdentity() throws Exception {
    var manifest =
        mock(
            com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest
                .class);
    KubernetesStreamingEvent first =
        new KubernetesStreamingEvent(KubernetesStreamingEvent.Type.UPSERT, manifest);
    KubernetesStreamingEvent second =
        new KubernetesStreamingEvent(KubernetesStreamingEvent.Type.UPSERT, manifest);
    BlockingQueue<KubernetesStreamingEvent> queue = new LinkedBlockingQueue<>();

    state.enqueueEvent(queue, first);
    state.enqueueEvent(queue, second);
    state.updateLastProcessedEventBatchTime(List.of(first));
    state.updateLastProcessedEventBatchTime(List.of(second));

    assertThatThrownBy(() -> state.updateLastProcessedEventBatchTime(List.of(first)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void publishingTheSameIdentityTwiceIsRejectedBeforeASecondQueuePut() throws Exception {
    BlockingQueue<KubernetesStreamingEvent> queue = mock(BlockingQueue.class);
    KubernetesStreamingEvent event = mock(KubernetesStreamingEvent.class);

    state.enqueueEvent(queue, event);

    assertThatThrownBy(() -> state.enqueueEvent(queue, event))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already");
    verify(queue).put(event);
    state.updateLastProcessedEventBatchTime(List.of(event));
  }

  @Test
  void failedQueuePutLeavesNoEventAccounting() throws Exception {
    BlockingQueue<KubernetesStreamingEvent> queue = mock(BlockingQueue.class);
    KubernetesStreamingEvent event = mock(KubernetesStreamingEvent.class);
    IllegalStateException queueFailure = new IllegalStateException("queue failed");
    doThrow(queueFailure).when(queue).put(event);

    assertThatThrownBy(() -> state.enqueueEvent(queue, event)).isSameAs(queueFailure);

    assertNoOutstandingEvents();
    org.mockito.Mockito.reset(queue);
    state.enqueueEvent(queue, event);
    state.updateLastProcessedEventBatchTime(List.of(event));
  }

  @Test
  void interruptedQueuePutLeavesNoEventAccounting() throws Exception {
    BlockingQueue<KubernetesStreamingEvent> queue = mock(BlockingQueue.class);
    KubernetesStreamingEvent event = mock(KubernetesStreamingEvent.class);
    InterruptedException interruption = new InterruptedException("queue interrupted");
    doThrow(interruption).when(queue).put(event);

    assertThatThrownBy(() -> state.enqueueEvent(queue, event)).isSameAs(interruption);

    assertNoOutstandingEvents();
    org.mockito.Mockito.reset(queue);
    state.enqueueEvent(queue, event);
    state.updateLastProcessedEventBatchTime(List.of(event));
  }

  @Test
  void concurrentProducersAndBatchAccountingLeaveNoOutstandingEvents() throws Exception {
    int producerCount = 6;
    BlockingQueue<KubernetesStreamingEvent> queue = mock(BlockingQueue.class);
    CountDownLatch allEventsPublished = new CountDownLatch(producerCount);
    CountDownLatch allowPublicationAccounting = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              allEventsPublished.countDown();
              if (!allowPublicationAccounting.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to account publication");
              }
              return null;
            })
        .when(queue)
        .put(org.mockito.ArgumentMatchers.any());
    ExecutorService workers = Executors.newFixedThreadPool(producerCount + 2);
    List<Future<?>> producers = new ArrayList<>();
    List<KubernetesStreamingEvent> events = new ArrayList<>();
    AtomicReference<Thread> firstProcessor = new AtomicReference<>();
    AtomicReference<Thread> secondProcessor = new AtomicReference<>();
    try {
      for (int i = 0; i < producerCount; i++) {
        KubernetesStreamingEvent event = mock(KubernetesStreamingEvent.class);
        events.add(event);
        producers.add(
            workers.submit(
                () -> {
                  try {
                    state.enqueueEvent(queue, event);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                  }
                }));
      }
      assertThat(allEventsPublished.await(5, TimeUnit.SECONDS)).isTrue();

      Future<?> firstBatch =
          workers.submit(
              () -> {
                firstProcessor.set(Thread.currentThread());
                state.updateLastProcessedEventBatchTime(List.copyOf(events.subList(0, 2)));
              });
      Future<?> secondBatch =
          workers.submit(
              () -> {
                secondProcessor.set(Thread.currentThread());
                state.updateLastProcessedEventBatchTime(List.copyOf(events.subList(2, 6)));
              });
      await(() -> isWaiting(firstProcessor.get()) && isWaiting(secondProcessor.get()));

      allowPublicationAccounting.countDown();
      for (Future<?> producer : producers) {
        producer.get(5, TimeUnit.SECONDS);
      }
      firstBatch.get(5, TimeUnit.SECONDS);
      secondBatch.get(5, TimeUnit.SECONDS);

      assertThat(state.getLastReceivedEventTime()).isEqualTo(START_TIME_MILLIS);
      assertThatThrownBy(() -> state.updateLastProcessedEventBatchTime(List.of(events.get(0))))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("unknown");
    } finally {
      allowPublicationAccounting.countDown();
      workers.shutdownNow();
      assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void failedBatchIsDroppedAndRemainsTerminalAfterLaterSuccess() throws Exception {
    KubernetesStreamingEvent failedEvent = enqueueAt(20_000);

    state.recordFailedEventBatch(
        List.of(failedEvent), new IllegalStateException("cache write failed"));

    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);
    assertThat(warningMessages())
        .anyMatch(
            message ->
                message.contains("processor")
                    && message.contains("IllegalStateException: cache write failed")
                    && message.contains("20000 ms"));
    assertThatThrownBy(
            () ->
                state.recordFailedEventBatch(
                    List.of(failedEvent), new IllegalStateException("processed twice")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unknown");

    KubernetesStreamingEvent successfulEvent = enqueueAt(20_100);
    state.updateLastProcessedEventBatchTime(List.of(successfulEvent));
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);
  }

  @Test
  void sustainedFailedBatchesReleaseExactIdentitiesAndRetainOneBoundedFailure() throws Exception {
    BlockingQueue<KubernetesStreamingEvent> queue = mock(BlockingQueue.class);
    KubernetesStreamingEvent reusedEvent = mock(KubernetesStreamingEvent.class);
    String longMessage = "x".repeat(2_000) + "not-retained-tail";

    for (int i = 0; i < 100; i++) {
      currentTimeMillis.set(20_000 + i);
      state.enqueueEvent(queue, reusedEvent);
      state.recordFailedEventBatch(
          List.of(reusedEvent),
          new IllegalStateException(i == 0 ? longMessage : "later failure " + i));
    }

    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);
    String processingFailure =
        warningMessages().stream()
            .filter(message -> message.contains("processor dropped event batch"))
            .findFirst()
            .orElseThrow();
    assertThat(processingFailure).doesNotContain("not-retained-tail").hasSizeLessThan(1_000);
    assertThatThrownBy(
            () ->
                state.recordFailedEventBatch(
                    List.of(reusedEvent), new IllegalStateException("already dropped")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void invalidFailedBatchesDoNotMutateEventsOrSetTerminalFailure() throws Exception {
    KubernetesStreamingEvent first = enqueueAt(20_000);
    KubernetesStreamingEvent second = enqueueAt(20_100);
    KubernetesStreamingEvent unknown = mock(KubernetesStreamingEvent.class);
    IllegalStateException processingFailure = new IllegalStateException("cache failed");

    assertThatThrownBy(() -> state.recordFailedEventBatch(List.of(first, first), processingFailure))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("duplicate");
    assertThatThrownBy(
            () -> state.recordFailedEventBatch(List.of(first, unknown), processingFailure))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unknown");

    state.updateLastProcessedEventBatchTime(List.of(first, second));
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);
  }

  @Test
  void failedBatchAccountingWaitsForItsPendingPublication() throws Exception {
    BlockingQueue<KubernetesStreamingEvent> queue = mock(BlockingQueue.class);
    KubernetesStreamingEvent event = mock(KubernetesStreamingEvent.class);
    CountDownLatch putStarted = new CountDownLatch(1);
    CountDownLatch allowPutToReturn = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              putStarted.countDown();
              if (!allowPutToReturn.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for queue put");
              }
              return null;
            })
        .when(queue)
        .put(event);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    AtomicReference<Thread> failureHandlerThread = new AtomicReference<>();
    try {
      Future<?> publication =
          workers.submit(
              () -> {
                try {
                  state.enqueueEvent(queue, event);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(e);
                }
              });
      assertThat(putStarted.await(5, TimeUnit.SECONDS)).isTrue();
      Future<?> failureAccounting =
          workers.submit(
              () -> {
                failureHandlerThread.set(Thread.currentThread());
                state.recordFailedEventBatch(
                    List.of(event), new IllegalStateException("cache failed"));
              });
      await(() -> isWaiting(failureHandlerThread.get()));

      allowPutToReturn.countDown();
      publication.get(5, TimeUnit.SECONDS);
      failureAccounting.get(5, TimeUnit.SECONDS);

      assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
          .isEqualTo(LongRunningAgentExecutionState.FAILED);
      assertThatThrownBy(
              () ->
                  state.recordFailedEventBatch(
                      List.of(event), new IllegalStateException("already dropped")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("unknown");
    } finally {
      allowPutToReturn.countDown();
      workers.shutdownNow();
      assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void processingDoesNotWaitForAnUnrelatedPendingPublication() throws Exception {
    BlockingQueue<KubernetesStreamingEvent> queue = mock(BlockingQueue.class);
    KubernetesStreamingEvent pending = mock(KubernetesStreamingEvent.class);
    KubernetesStreamingEvent unknown = mock(KubernetesStreamingEvent.class);
    CountDownLatch putStarted = new CountDownLatch(1);
    CountDownLatch allowPutToReturn = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              putStarted.countDown();
              if (!allowPutToReturn.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for queue put");
              }
              return null;
            })
        .when(queue)
        .put(pending);
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      Future<?> publication =
          worker.submit(
              () -> {
                try {
                  state.enqueueEvent(queue, pending);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(e);
                }
              });
      assertThat(putStarted.await(5, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> state.updateLastProcessedEventBatchTime(List.of(unknown)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("unknown");

      allowPutToReturn.countDown();
      publication.get(5, TimeUnit.SECONDS);
      state.updateLastProcessedEventBatchTime(List.of(pending));
    } finally {
      allowPutToReturn.countDown();
      worker.shutdownNow();
      assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void stopReleasesBatchAccountingWaitingForPublication() throws Exception {
    BlockingQueue<KubernetesStreamingEvent> queue = mock(BlockingQueue.class);
    CountDownLatch eventPublished = new CountDownLatch(1);
    CountDownLatch allowPutToReturn = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              eventPublished.countDown();
              if (!allowPutToReturn.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for queue put to return");
              }
              return null;
            })
        .when(queue)
        .put(org.mockito.ArgumentMatchers.any());
    ExecutorService workers = Executors.newFixedThreadPool(2);
    AtomicReference<Thread> processor = new AtomicReference<>();
    KubernetesStreamingEvent event = mock(KubernetesStreamingEvent.class);
    try {
      Future<?> producer =
          workers.submit(
              () -> {
                try {
                  state.enqueueEvent(queue, event);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(e);
                }
              });
      assertThat(eventPublished.await(5, TimeUnit.SECONDS)).isTrue();
      Future<?> processing =
          workers.submit(
              () -> {
                processor.set(Thread.currentThread());
                state.updateLastProcessedEventBatchTime(List.of(event));
              });
      await(() -> isWaiting(processor.get()));

      state.stopAndWait(0);

      processing.get(5, TimeUnit.SECONDS);
      when(executor.isTerminated()).thenReturn(false);
      assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
          .isEqualTo(LongRunningAgentExecutionState.CLEANING_UP);
      allowPutToReturn.countDown();
      producer.get(5, TimeUnit.SECONDS);
    } finally {
      allowPutToReturn.countDown();
      workers.shutdownNow();
      assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void tickerRollbackBeforeReadinessFailsExplicitly() {
    currentTimeMillis.set(START_TIME_MILLIS - 1);

    assertThat(state.getState(1_000, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);
    assertThat(warningMessages()).anyMatch(message -> message.contains("ticker moved backwards"));
    verify(kubernetesWatcherFactory, never()).allWatchersHealthy(anyLong());
  }

  @Test
  void tickerRollbackBehindAnOutstandingEventFailsExplicitly() throws Exception {
    enqueueAt(START_TIME_MILLIS + 500);
    currentTimeMillis.set(START_TIME_MILLIS + 400);

    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);
    assertThat(warningMessages()).anyMatch(message -> message.contains("ticker moved backwards"));
  }

  @Test
  void enqueueAfterBacklogSnapshotDoesNotCreateFalseTickerRollback() throws Exception {
    BlockingClock clock = new BlockingClock(100);
    State concurrentState = newState(clock);
    concurrentState.monitorWorkers(new CompletableFuture<>(), new CompletableFuture<>());
    concurrentState.start();
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      clock.blockNextCallFrom("state-check");
      Future<LongRunningAgentExecutionState> observedState =
          worker.submit(
              () -> {
                Thread.currentThread().setName("state-check");
                return concurrentState.getState(0, LIVENESS_TIMEOUT_MILLIS);
              });
      assertThat(clock.awaitBlockedCall()).isTrue();

      clock.set(200);
      concurrentState.enqueueEvent(
          new LinkedBlockingQueue<>(), mock(KubernetesStreamingEvent.class));
      clock.releaseBlockedCall();

      assertThat(observedState.get(5, TimeUnit.SECONDS))
          .isEqualTo(LongRunningAgentExecutionState.RUNNING);
    } finally {
      clock.releaseBlockedCall();
      worker.shutdownNow();
      assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void elapsedChecksRemainCorrectNearLongMaxValue() throws Exception {
    currentTimeMillis.set(Long.MAX_VALUE - 2_000);
    state = newState(currentTimeMillis::get);
    state.monitorWorkers(batcherFuture, processorFuture);
    state.start();

    currentTimeMillis.set(Long.MAX_VALUE - 1_000);
    assertThat(state.getState(1_000, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);

    KubernetesStreamingEvent event = enqueueAt(Long.MAX_VALUE - 1_000);
    currentTimeMillis.set(Long.MAX_VALUE - 1);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);
    currentTimeMillis.set(Long.MAX_VALUE);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);
    state.updateLastProcessedEventBatchTime(List.of(event));
  }

  @Test
  void readinessGraceReturnsRunningBeforeEvaluatingComponentHealth() {
    when(kubernetesWatcherFactory.allWatchersHealthy(anyLong())).thenReturn(false);
    batcherFuture.complete(null);
    processorFuture.completeExceptionally(new IllegalStateException("processor failed"));
    currentTimeMillis.set(START_TIME_MILLIS + 999);

    assertThat(state.getState(1_000, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.RUNNING);
    verify(kubernetesWatcherFactory, never()).allWatchersHealthy(anyLong());

    currentTimeMillis.set(START_TIME_MILLIS + 1_000);
    assertThat(state.getState(1_000, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.FAILED);
    verify(kubernetesWatcherFactory).allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS);
  }

  @Test
  void stopDuringReadinessEvaluationWinsOverRunning() throws Exception {
    BlockingClock clock = new BlockingClock(START_TIME_MILLIS);
    State concurrentState = newState(clock);
    concurrentState.monitorWorkers(new CompletableFuture<>(), new CompletableFuture<>());
    concurrentState.start();
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      clock.blockNextCallFrom("state-check");
      Future<LongRunningAgentExecutionState> observedState =
          worker.submit(
              () -> {
                Thread.currentThread().setName("state-check");
                return concurrentState.getState(1_000, LIVENESS_TIMEOUT_MILLIS);
              });
      assertThat(clock.awaitBlockedCall()).isTrue();

      concurrentState.stopAndWait(0);
      clock.releaseBlockedCall();

      assertThat(observedState.get(5, TimeUnit.SECONDS))
          .isEqualTo(LongRunningAgentExecutionState.CLEANING_UP);
    } finally {
      clock.releaseBlockedCall();
      worker.shutdownNow();
      assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void stopDuringSuccessfulWatcherHealthCheckWinsOverRunning() throws Exception {
    doAnswer(
            invocation -> {
              state.stopAndWait(0);
              return true;
            })
        .when(kubernetesWatcherFactory)
        .allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS);

    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.CLEANING_UP);
  }

  @Test
  void notStartedAndStoppedStatesTakePrecedenceOverFailedComponents() throws Exception {
    State notStarted = newState(currentTimeMillis::get);
    assertThat(notStarted.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.NOT_RUNNING);

    state.stopAndWait(0);
    batcherFuture.cancel(true);
    processorFuture.completeExceptionally(new IllegalStateException("processor stopped"));
    when(kubernetesWatcherFactory.allWatchersHealthy(anyLong())).thenReturn(false);
    clearInvocations(kubernetesWatcherFactory);

    when(executor.isTerminated()).thenReturn(false);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.CLEANING_UP);
    when(executor.isTerminated()).thenReturn(true);
    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.NOT_RUNNING);
    verify(kubernetesWatcherFactory, never()).allWatchersHealthy(anyLong());
  }

  @Test
  void stopThatCancelsWorkerDuringHealthCheckReturnsCleaningUp() {
    CompletableFuture<Void> stoppingBatcher =
        new CompletableFuture<>() {
          private boolean stopTriggered;

          @Override
          public boolean isCancelled() {
            if (!stopTriggered) {
              stopTriggered = true;
              try {
                state.stopAndWait(0);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
              }
            }
            return true;
          }
        };
    state.monitorWorkers(stoppingBatcher, processorFuture);
    when(executor.isTerminated()).thenReturn(false);

    assertThat(state.getState(0, LIVENESS_TIMEOUT_MILLIS))
        .isEqualTo(LongRunningAgentExecutionState.CLEANING_UP);
    assertThat(warningMessages()).noneMatch(message -> message.contains("batcher"));
  }

  private State newState(LongSupplier clock) {
    return new State("test-account", executor, kubernetesWatcherFactory, new OkHttpClient(), clock);
  }

  private State startedStateWithoutWorkers() {
    State unmonitored = newState(currentTimeMillis::get);
    unmonitored.start();
    return unmonitored;
  }

  private KubernetesStreamingEvent enqueueAt(long timeMillis) throws InterruptedException {
    currentTimeMillis.set(timeMillis);
    KubernetesStreamingEvent event = mock(KubernetesStreamingEvent.class);
    state.enqueueEvent(new LinkedBlockingQueue<>(), event);
    return event;
  }

  private void assertNoOutstandingEvents() {
    assertThat(state.getLastReceivedEventTime()).isZero();
    assertThatThrownBy(
            () ->
                state.updateLastProcessedEventBatchTime(
                    List.of(mock(KubernetesStreamingEvent.class))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unknown");
  }

  private static boolean isWaiting(Thread thread) {
    return thread != null && thread.getState() == Thread.State.WAITING;
  }

  private List<String> warningMessages() {
    return logAppender.list.stream()
        .filter(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  private static void await(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(10);
    }
    throw new AssertionError("Timed out waiting for concurrent state transition");
  }

  private enum WorkerCompletion {
    NORMAL("completed normally") {
      @Override
      CompletableFuture<Void> future() {
        return CompletableFuture.completedFuture(null);
      }
    },
    EXCEPTIONAL("completed exceptionally") {
      @Override
      CompletableFuture<Void> future() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("worker failed"));
        return future;
      }
    },
    CANCELLED("was cancelled") {
      @Override
      CompletableFuture<Void> future() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.cancel(true);
        return future;
      }
    };

    private final String logDescription;

    WorkerCompletion(String logDescription) {
      this.logDescription = logDescription;
    }

    abstract CompletableFuture<Void> future();
  }

  private static final class BlockingClock implements LongSupplier {
    private final AtomicLong currentTimeMillis;
    private final CountDownLatch blockedCallEntered = new CountDownLatch(1);
    private final CountDownLatch releaseBlockedCall = new CountDownLatch(1);
    private volatile String blockedThreadName;

    private BlockingClock(long initialTimeMillis) {
      currentTimeMillis = new AtomicLong(initialTimeMillis);
    }

    @Override
    public long getAsLong() {
      long sampledTimeMillis = currentTimeMillis.get();
      if (Thread.currentThread().getName().equals(blockedThreadName)) {
        blockedThreadName = null;
        blockedCallEntered.countDown();
        try {
          if (!releaseBlockedCall.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to release blocked clock call");
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while waiting to release clock call", e);
        }
      }
      return sampledTimeMillis;
    }

    private void set(long timeMillis) {
      currentTimeMillis.set(timeMillis);
    }

    private void blockNextCallFrom(String threadName) {
      blockedThreadName = threadName;
    }

    private boolean awaitBlockedCall() throws InterruptedException {
      return blockedCallEntered.await(5, TimeUnit.SECONDS);
    }

    private void releaseBlockedCall() {
      releaseBlockedCall.countDown();
    }
  }
}
