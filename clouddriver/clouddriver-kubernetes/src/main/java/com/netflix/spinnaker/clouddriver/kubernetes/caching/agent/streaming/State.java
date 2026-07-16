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

import com.netflix.spinnaker.cats.agent.LongRunningAgentExecutionState;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
@ToString(
    of = {
      "agentType",
      "started",
      "stopped",
      "startTimeMillis",
      "lastReceivedEventTimeMillis",
      "lastProcessedEventBatchTimeMillis",
      "oldestOutstandingEventTimeMillis"
    })
class State {

  private static final int MAX_PROCESSING_FAILURE_DESCRIPTION_LENGTH = 512;

  private final String agentType;
  private final ExecutorService executorService;
  private final KubernetesStreamingWatcherFactory factory;
  private final LongSupplier tickerMillis;

  /**
   * The OkHttp client shared by all watchers for this agent. By default, OkHttp does not cancel
   * requests when a thread is interrupted. This is a workaround to ensure that all requests are
   * cancelled when the agent is stopped. Without this, the agent may get stuck waiting for a
   * response from the Kubernetes API.
   */
  private final OkHttpClient watcherHttpClient;

  private final AtomicLong lastReceivedEventTimeMillis = new AtomicLong();
  private final AtomicLong lastProcessedEventBatchTimeMillis = new AtomicLong();
  private final long startTimeMillis;
  private volatile CompletableFuture<Void> batcherFuture;
  private volatile CompletableFuture<Void> processorFuture;
  private final IdentityHashMap<KubernetesStreamingEvent, Boolean> pendingEvents =
      new IdentityHashMap<>();
  private final IdentityHashMap<KubernetesStreamingEvent, Long> outstandingEvents =
      new IdentityHashMap<>();
  private final NavigableMap<Long, Integer> outstandingEventTimes = new TreeMap<>();
  private long oldestOutstandingEventTimeMillis;
  private volatile ProcessingFailure terminalProcessingFailure;
  private volatile boolean started = false;
  private volatile boolean stopped = false;

  State(
      String agentType,
      ExecutorService executorService,
      KubernetesStreamingWatcherFactory factory,
      OkHttpClient watcherHttpClient) {
    this(
        agentType,
        executorService,
        factory,
        watcherHttpClient,
        () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
  }

  State(
      String agentType,
      ExecutorService executorService,
      KubernetesStreamingWatcherFactory factory,
      OkHttpClient watcherHttpClient,
      LongSupplier tickerMillis) {
    if (agentType == null
        || executorService == null
        || factory == null
        || watcherHttpClient == null
        || tickerMillis == null) {
      throw new IllegalStateException(
          "agentType, executorService, factory, watcherHttpClient and tickerMillis must not be null");
    }

    this.agentType = agentType;
    this.executorService = executorService;
    this.factory = factory;
    this.watcherHttpClient = watcherHttpClient;
    this.tickerMillis = tickerMillis;
    this.startTimeMillis = tickerMillis.getAsLong();
  }

  void monitorWorkers(
      CompletableFuture<Void> batcherFuture, CompletableFuture<Void> processorFuture) {
    this.batcherFuture = batcherFuture;
    this.processorFuture = processorFuture;
  }

  void start() {
    if (started) {
      throw new IllegalStateException("Already started");
    }
    started = true;
    log.info("Started streaming agent {}", agentType);
  }

  boolean stopAndWait(long timeoutMs) throws InterruptedException {
    synchronized (this) {
      if (stopped) {
        throw new IllegalStateException("Already stopped");
      }
      stopped = true;
      notifyAll();
    }
    Throwable cleanupFailure = null;
    try {
      factory.stopAllRegisteredWatchers();
    } catch (RuntimeException | Error e) {
      cleanupFailure = e;
    }
    try {
      executorService.shutdownNow();
    } catch (RuntimeException | Error e) {
      cleanupFailure = addCleanupFailure(cleanupFailure, e);
    }
    try {
      watcherHttpClient.dispatcher().cancelAll();
    } catch (RuntimeException | Error e) {
      cleanupFailure = addCleanupFailure(cleanupFailure, e);
    }

    boolean terminated = false;
    try {
      terminated = awaitTerminationPreservingInterrupt(timeoutMs);
    } catch (InterruptedException e) {
      if (cleanupFailure != null) {
        e.addSuppressed(cleanupFailure);
      }
      throw e;
    }
    if (cleanupFailure instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    if (cleanupFailure instanceof Error error) {
      throw error;
    }
    return terminated;
  }

  private boolean awaitTerminationPreservingInterrupt(long timeoutMs) throws InterruptedException {
    long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMs));
    long waitStartedNanos = System.nanoTime();
    long remainingNanos = timeoutNanos;
    InterruptedException interruption = null;
    boolean terminated = false;
    do {
      try {
        terminated = executorService.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS);
        break;
      } catch (InterruptedException e) {
        if (interruption == null) {
          interruption = e;
        } else {
          interruption.addSuppressed(e);
        }
      }
      long elapsedNanos = System.nanoTime() - waitStartedNanos;
      remainingNanos = elapsedNanos >= timeoutNanos ? 0 : timeoutNanos - elapsedNanos;
    } while (remainingNanos > 0);

    if (interruption != null) {
      Thread.currentThread().interrupt();
      throw interruption;
    }
    return terminated;
  }

  private static Throwable addCleanupFailure(
      Throwable cleanupFailure, Throwable additionalFailure) {
    if (cleanupFailure == null) {
      return additionalFailure;
    }
    if (cleanupFailure != additionalFailure) {
      cleanupFailure.addSuppressed(additionalFailure);
    }
    return cleanupFailure;
  }

  KubernetesStreamingWatcherFactory getFactory() {
    return factory;
  }

  LongRunningAgentExecutionState getState(long readinessTimeoutMs, long livenessTimeoutMs) {
    LongRunningAgentExecutionState stoppedState = getStoppedState();
    if (stoppedState != null) {
      return stoppedState;
    }

    if (!started) {
      log.info("Streaming agent {} not started yet, returning NOT_RUNNING state", agentType);
      return LongRunningAgentExecutionState.NOT_RUNNING;
    }

    Long oldestOutstandingEvent;
    synchronized (this) {
      oldestOutstandingEvent =
          outstandingEvents.isEmpty() ? null : oldestOutstandingEventTimeMillis;
    }
    long now = tickerMillis.getAsLong();
    Long agentAgeMillis = elapsedMillis(now, startTimeMillis, "agent age");
    if (agentAgeMillis == null) {
      return stateUnlessStopped(LongRunningAgentExecutionState.FAILED);
    }
    if (agentAgeMillis < readinessTimeoutMs) {
      return stateUnlessStopped(LongRunningAgentExecutionState.RUNNING);
    }

    if (!factory.allWatchersHealthy(livenessTimeoutMs)) {
      stoppedState = getStoppedState();
      if (stoppedState != null) {
        return stoppedState;
      }
      log.warn(
          "Streaming agent {} FAILED liveness check: watcher health check failed "
              + "(agent age {} ms, liveness limit {} ms)",
          agentType,
          agentAgeMillis,
          livenessTimeoutMs);
      return stateUnlessStopped(LongRunningAgentExecutionState.FAILED);
    }

    String component = "batcher";
    String workerFailure = workerFailure(batcherFuture);
    if (workerFailure == null) {
      component = "processor";
      workerFailure = workerFailure(processorFuture);
    }
    if (workerFailure != null) {
      stoppedState = getStoppedState();
      if (stoppedState != null) {
        return stoppedState;
      }
      log.warn(
          "Streaming agent {} FAILED liveness check: {} worker {} "
              + "(agent age {} ms, liveness limit {} ms)",
          agentType,
          component,
          workerFailure,
          agentAgeMillis,
          livenessTimeoutMs);
      return stateUnlessStopped(LongRunningAgentExecutionState.FAILED);
    }

    ProcessingFailure processingFailure = terminalProcessingFailure;
    if (processingFailure != null) {
      stoppedState = getStoppedState();
      if (stoppedState != null) {
        return stoppedState;
      }
      log.warn(
          "Streaming agent {} FAILED liveness check: processor dropped event batch at {} ms after {}. "
              + "Restart/relist is required",
          agentType,
          processingFailure.timestampMillis,
          processingFailure.description);
      return stateUnlessStopped(LongRunningAgentExecutionState.FAILED);
    }

    if (oldestOutstandingEvent != null) {
      Long backlogAgeMillis = elapsedMillis(now, oldestOutstandingEvent, "backlog age");
      if (backlogAgeMillis == null) {
        return stateUnlessStopped(LongRunningAgentExecutionState.FAILED);
      }
      if (backlogAgeMillis >= livenessTimeoutMs) {
        stoppedState = getStoppedState();
        if (stoppedState != null) {
          return stoppedState;
        }
        log.warn(
            "Streaming agent {} FAILED liveness check: oldest backlog event age {} ms "
                + "reached liveness limit {} ms (agent age {} ms)",
            agentType,
            backlogAgeMillis,
            livenessTimeoutMs,
            agentAgeMillis);
        return stateUnlessStopped(LongRunningAgentExecutionState.FAILED);
      }
    }

    return stateUnlessStopped(LongRunningAgentExecutionState.RUNNING);
  }

  private LongRunningAgentExecutionState stateUnlessStopped(
      LongRunningAgentExecutionState candidate) {
    LongRunningAgentExecutionState stoppedState = getStoppedState();
    return stoppedState == null ? candidate : stoppedState;
  }

  private Long elapsedMillis(long now, long earlier, String measurement) {
    if (now < earlier) {
      log.warn(
          "Streaming agent {} FAILED liveness check: ticker moved backwards while measuring {} "
              + "(now {} ms, previous {} ms)",
          agentType,
          measurement,
          now,
          earlier);
      return null;
    }
    try {
      return Math.subtractExact(now, earlier);
    } catch (ArithmeticException e) {
      log.warn(
          "Streaming agent {} FAILED liveness check: ticker elapsed time overflow while measuring {} "
              + "(now {} ms, previous {} ms)",
          agentType,
          measurement,
          now,
          earlier);
      return null;
    }
  }

  private LongRunningAgentExecutionState getStoppedState() {
    if (!stopped) {
      return null;
    }
    if (executorService.isTerminated()) {
      log.info(
          "Streaming agent {} has stopped and executor service is terminated, returning NOT_RUNNING state",
          agentType);
      return LongRunningAgentExecutionState.NOT_RUNNING;
    }
    return LongRunningAgentExecutionState.CLEANING_UP;
  }

  private String workerFailure(CompletableFuture<Void> workerFuture) {
    if (workerFuture == null) {
      return "is not monitored";
    }
    if (workerFuture.isCancelled()) {
      return "was cancelled";
    }
    if (workerFuture.isCompletedExceptionally()) {
      return "completed exceptionally";
    }
    if (workerFuture.isDone()) {
      return "completed normally";
    }
    return null;
  }

  void enqueueEvent(
      BlockingQueue<KubernetesStreamingEvent> eventQueue, KubernetesStreamingEvent event)
      throws InterruptedException {
    synchronized (this) {
      if (pendingEvents.containsKey(event) || outstandingEvents.containsKey(event)) {
        throw new IllegalStateException("Event identity is already pending or outstanding");
      }
      pendingEvents.put(event, Boolean.TRUE);
    }

    boolean enqueued = false;
    try {
      eventQueue.put(event);
      enqueued = true;
    } finally {
      synchronized (this) {
        pendingEvents.remove(event);
        try {
          if (enqueued) {
            commitEnqueuedEvent(event);
          }
        } finally {
          notifyAll();
        }
      }
    }
  }

  private void commitEnqueuedEvent(KubernetesStreamingEvent event) {
    long now = tickerMillis.getAsLong();
    outstandingEvents.put(event, now);
    outstandingEventTimes.merge(now, 1, Integer::sum);
    oldestOutstandingEventTimeMillis = outstandingEventTimes.firstKey();
    if (lastReceivedEventTimeMillis.get() == 0) {
      Long elapsedMillis = elapsedMillis(now, startTimeMillis, "first event receipt");
      log.info(
          "Streaming agent {}: FIRST event received ({}ms after start).",
          agentType,
          elapsedMillis == null ? "unknown" : elapsedMillis);
    }
    lastReceivedEventTimeMillis.set(now);
  }

  long getLastReceivedEventTime() {
    return lastReceivedEventTimeMillis.get();
  }

  synchronized void updateLastProcessedEventBatchTime(List<KubernetesStreamingEvent> batch) {
    IdentityHashMap<KubernetesStreamingEvent, Boolean> batchEvents =
        waitForAndValidateBatch(batch, "Processed");
    if (batchEvents == null) {
      return;
    }

    long now = tickerMillis.getAsLong();
    removeOutstandingEvents(batchEvents);
    boolean isFirstBatch = lastProcessedEventBatchTimeMillis.get() == 0;
    lastProcessedEventBatchTimeMillis.set(now);
    if (isFirstBatch) {
      Long elapsedMillis = elapsedMillis(now, startTimeMillis, "first batch processing");
      log.info(
          "Streaming agent {}: FIRST batch processed ({}ms after start).",
          agentType,
          elapsedMillis == null ? "unknown" : elapsedMillis);
    }
  }

  synchronized void recordFailedEventBatch(
      List<KubernetesStreamingEvent> batch, Exception failure) {
    if (failure == null) {
      throw new IllegalArgumentException("Processing failure must not be null");
    }
    IdentityHashMap<KubernetesStreamingEvent, Boolean> batchEvents =
        waitForAndValidateBatch(batch, "Failed");
    if (batchEvents == null) {
      return;
    }

    String failureDescription = null;
    long failureTimeMillis = 0;
    if (terminalProcessingFailure == null) {
      failureDescription = boundedFailureDescription(failure);
      failureTimeMillis = tickerMillis.getAsLong();
    }
    removeOutstandingEvents(batchEvents);
    if (terminalProcessingFailure == null) {
      terminalProcessingFailure = new ProcessingFailure(failureDescription, failureTimeMillis);
    }
  }

  private IdentityHashMap<KubernetesStreamingEvent, Boolean> waitForAndValidateBatch(
      List<KubernetesStreamingEvent> batch, String batchStatus) {
    if (batch == null || batch.isEmpty()) {
      throw new IllegalArgumentException(
          batchStatus + " event batch size must be positive: " + (batch == null ? "null" : 0));
    }

    IdentityHashMap<KubernetesStreamingEvent, Boolean> batchEvents = new IdentityHashMap<>();
    for (KubernetesStreamingEvent event : batch) {
      if (batchEvents.put(event, Boolean.TRUE) != null) {
        throw new IllegalStateException(batchStatus + " event batch contains a duplicate identity");
      }
    }

    while (containsPendingEvent(batchEvents) && !stopped) {
      try {
        wait();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        if (stopped) {
          return null;
        }
        throw new IllegalStateException(
            "Interrupted while waiting for event enqueue accounting", e);
      }
    }
    if (stopped) {
      return null;
    }
    for (KubernetesStreamingEvent event : batchEvents.keySet()) {
      if (!outstandingEvents.containsKey(event)) {
        throw new IllegalStateException(
            batchStatus + " event batch contains an unknown event identity");
      }
    }
    return batchEvents;
  }

  private void removeOutstandingEvents(
      IdentityHashMap<KubernetesStreamingEvent, Boolean> batchEvents) {
    HashMap<Long, Integer> removedEventTimes = new HashMap<>(batchEvents.size());
    for (KubernetesStreamingEvent event : batchEvents.keySet()) {
      Long receiptTimeMillis = outstandingEvents.get(event);
      if (receiptTimeMillis == null) {
        throw new IllegalStateException(
            "Outstanding event identity has no receipt time during removal");
      }
      removedEventTimes.merge(receiptTimeMillis, 1, Integer::sum);
    }

    for (var removedEventTime : removedEventTimes.entrySet()) {
      Integer outstandingEventCount = outstandingEventTimes.get(removedEventTime.getKey());
      if (outstandingEventCount == null
          || outstandingEventCount <= 0
          || outstandingEventCount < removedEventTime.getValue()) {
        throw new IllegalStateException(
            "Outstanding event timestamp index has invalid count "
                + outstandingEventCount
                + " for "
                + removedEventTime.getValue()
                + " removals at receipt time "
                + removedEventTime.getKey());
      }
    }

    for (KubernetesStreamingEvent event : batchEvents.keySet()) {
      outstandingEvents.remove(event);
    }
    for (var removedEventTime : removedEventTimes.entrySet()) {
      long receiptTimeMillis = removedEventTime.getKey();
      int remainingCount =
          outstandingEventTimes.get(receiptTimeMillis) - removedEventTime.getValue();
      if (remainingCount == 0) {
        outstandingEventTimes.remove(receiptTimeMillis);
      } else {
        outstandingEventTimes.put(receiptTimeMillis, remainingCount);
      }
    }
    oldestOutstandingEventTimeMillis =
        outstandingEventTimes.isEmpty() ? 0 : outstandingEventTimes.firstKey();
  }

  private static String boundedFailureDescription(Exception failure) {
    String failureType = failure.getClass().getSimpleName();
    if (failureType.isEmpty()) {
      failureType = failure.getClass().getName();
    }
    String description = failureType;
    if (failure.getMessage() != null && !failure.getMessage().isEmpty()) {
      description += ": " + failure.getMessage();
    }
    if (description.length() <= MAX_PROCESSING_FAILURE_DESCRIPTION_LENGTH) {
      return description;
    }
    return description.substring(0, MAX_PROCESSING_FAILURE_DESCRIPTION_LENGTH - 3) + "...";
  }

  private boolean containsPendingEvent(
      IdentityHashMap<KubernetesStreamingEvent, Boolean> batchEvents) {
    for (KubernetesStreamingEvent event : batchEvents.keySet()) {
      if (pendingEvents.containsKey(event)) {
        return true;
      }
    }
    return false;
  }

  long getLastProcessedEventBatchTime() {
    return lastProcessedEventBatchTimeMillis.get();
  }

  private static final class ProcessingFailure {
    private final String description;
    private final long timestampMillis;

    private ProcessingFailure(String description, long timestampMillis) {
      this.description = description;
      this.timestampMillis = timestampMillis;
    }
  }
}
