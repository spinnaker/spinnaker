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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.netflix.spinnaker.cats.agent.NoOpStartupConcurrencyControl;
import com.netflix.spinnaker.cats.agent.StartupConcurrencyControl;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

class KubernetesStreamingWatcherFactoryTest {

  private static final String ACCOUNT = "test-account";
  private static final long LIVENESS_TIMEOUT_MILLIS = 1_000;
  private static final long HEARTBEAT_TIME_MILLIS = 10_000;

  private ApiClient apiClient;
  private ExecutorService threadPool;
  private AtomicLong currentTimeMillis;
  private LongSupplier factoryClock;
  private TestKubernetesStreamingWatcherFactory factory;
  private Logger factoryLogger;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    apiClient = new ClientBuilder().setBasePath("http://localhost").build();
    threadPool = mock(ExecutorService.class);
    currentTimeMillis = new AtomicLong();
    factoryClock = currentTimeMillis::get;
    factory =
        new TestKubernetesStreamingWatcherFactory(
            apiClient, ACCOUNT, 200, threadPool, new NoOpStartupConcurrencyControl(), factoryClock);

    factoryLogger = (Logger) LoggerFactory.getLogger(KubernetesStreamingWatcherFactory.class);
    logAppender = new ListAppender<>();
    logAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    factoryLogger.addAppender(logAppender);
    logAppender.start();
  }

  @AfterEach
  void tearDown() {
    factoryLogger.detachAppender(logAppender);
    logAppender.stop();
  }

  @Test
  void watcherRegistrationPreservesConfiguredReadTimeout() {
    apiClient.setReadTimeout(25_000);

    registerWatcher(DynamicKubernetesObject.class, "Pod");

    assertThat(apiClient.getReadTimeout()).isEqualTo(25_000);
  }

  @Test
  void duplicateRegistrationReturnsCanonicalWatcher() {
    KubernetesStreamingWatcher canonical = registerWatcher(DynamicKubernetesObject.class, "Pod");

    KubernetesStreamingWatcher duplicate =
        registerWatcher(DynamicKubernetesObject.class, "DifferentPod");

    assertThat(duplicate).isSameAs(canonical);
  }

  @Test
  void factoryPassesItsClockToCreatedWatchers() {
    registerWatcher(DynamicKubernetesObject.class, "Pod");

    assertThat(factory.getLastWatcherClock()).isSameAs(factoryClock);
  }

  @Test
  void productionFactoryPassesAMonotonicTickerToCreatedWatchers() {
    TestKubernetesStreamingWatcherFactory productionFactory =
        new TestKubernetesStreamingWatcherFactory(
            apiClient, ACCOUNT, 200, threadPool, new NoOpStartupConcurrencyControl());
    productionFactory.addHeartbeatTime(HEARTBEAT_TIME_MILLIS);

    long before = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    productionFactory.watcherFor(
        DynamicKubernetesObject.class,
        "Pod",
        "",
        "v1",
        "pods",
        mock(State.class),
        new ArrayBlockingQueue<>(1),
        Set.of(),
        1_000,
        20,
        300);
    long observed = productionFactory.getLastWatcherClock().getAsLong();
    long after = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());

    assertThat(observed).isBetween(before, after);
  }

  @Test
  void registrationAfterStartIsRejected() {
    registerWatcher(DynamicKubernetesObject.class, "Pod");
    returnFutureFromThreadPool(new CompletableFuture<>());
    factory.startAllWatchers();

    assertThatThrownBy(() -> registerWatcher(PodApiType.class, "AnotherPod"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("after watchers have started");
  }

  @Test
  void repeatedStartIsIdempotent() {
    KubernetesStreamingWatcher watcher = registerWatcher(DynamicKubernetesObject.class, "Pod");
    returnFutureFromThreadPool(new CompletableFuture<>());

    factory.startAllWatchers();
    factory.startAllWatchers();

    verify(threadPool, times(1)).submit(watcher);
  }

  @Test
  void restartAfterStopIsRejected() {
    registerWatcher(DynamicKubernetesObject.class, "Pod");
    returnFutureFromThreadPool(new CompletableFuture<>());
    factory.startAllWatchers();
    factory.stopAllRegisteredWatchers();

    assertThatThrownBy(factory::startAllWatchers)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("after watchers have stopped");
  }

  @Test
  void submissionFailureCancelsPriorFuturesAndPreventsRestart() {
    CompletableFuture<Void> firstFuture = liveFuture();
    CompletableFuture<Void> secondFuture = liveFuture();
    RejectedExecutionException submissionFailure =
        new RejectedExecutionException("executor rejected watcher");
    registerThreeWatchers();
    doReturn(firstFuture)
        .doReturn(secondFuture)
        .doThrow(submissionFailure)
        .when(threadPool)
        .submit(any(Runnable.class));

    assertThatThrownBy(factory::startAllWatchers).isSameAs(submissionFailure);
    assertThat(firstFuture).isCancelled();
    assertThat(secondFuture).isCancelled();

    assertThatThrownBy(factory::startAllWatchers)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("after watchers have stopped");
    verify(threadPool, times(3)).submit(any(Runnable.class));
  }

  @Test
  void nullSubmissionFutureCancelsPriorFuturesAndPreventsRestart() {
    CompletableFuture<Void> firstFuture = liveFuture();
    CompletableFuture<Void> secondFuture = liveFuture();
    registerThreeWatchers();
    doReturn(firstFuture)
        .doReturn(secondFuture)
        .doReturn(null)
        .when(threadPool)
        .submit(any(Runnable.class));

    assertThatThrownBy(factory::startAllWatchers)
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Executor returned null watcher future");
    assertThat(firstFuture).isCancelled();
    assertThat(secondFuture).isCancelled();

    assertThatThrownBy(factory::startAllWatchers)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("after watchers have stopped");
    verify(threadPool, times(3)).submit(any(Runnable.class));
  }

  @Test
  void stopWaitsForConcurrentSubmissionAndCancelsItsFuture() throws Exception {
    CompletableFuture<Void> watcherFuture = new CompletableFuture<>();
    CountDownLatch submitEntered = new CountDownLatch(1);
    CountDownLatch allowSubmitToReturn = new CountDownLatch(1);
    CountDownLatch stopCallEntered = new CountDownLatch(1);
    registerWatcher(DynamicKubernetesObject.class, "Pod");
    factory.signalStopCallWith(stopCallEntered);
    doAnswer(
            invocation -> {
              submitEntered.countDown();
              assertThat(allowSubmitToReturn.await(5, TimeUnit.SECONDS)).isTrue();
              return watcherFuture;
            })
        .when(threadPool)
        .submit(any(Runnable.class));
    ExecutorService lifecycleCalls = Executors.newFixedThreadPool(2);

    try {
      Future<?> startCall = lifecycleCalls.submit(factory::startAllWatchers);
      assertThat(submitEntered.await(5, TimeUnit.SECONDS)).isTrue();
      Future<?> stopCall = lifecycleCalls.submit(factory::stopAllRegisteredWatchers);
      assertThat(stopCallEntered.await(5, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> stopCall.get(100, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);

      allowSubmitToReturn.countDown();
      startCall.get(5, TimeUnit.SECONDS);
      stopCall.get(5, TimeUnit.SECONDS);
      assertThat(watcherFuture).isCancelled();
    } finally {
      allowSubmitToReturn.countDown();
      lifecycleCalls.shutdownNow();
      assertThat(lifecycleCalls.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void noRegisteredWatchersIsUnhealthy() {
    assertThat(factory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS)).isFalse();
  }

  @Test
  void registeredWatcherWithoutExecutionFutureIsUnhealthy() {
    KubernetesStreamingWatcher watcher = registerWatcher(DynamicKubernetesObject.class, "Pod");

    assertThat(factory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS)).isFalse();
    assertThat(logMessages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(ACCOUNT, watcher.watcherId(), "has no execution future"));
  }

  @Test
  void allRegisteredWatchersWithLiveFuturesAndFreshHeartbeatsAreHealthy() {
    registerWatcher(PodApiType.class, "Pod");
    registerWatcher(DeploymentApiType.class, "Deployment");
    returnFuturesFromThreadPool(liveFuture(), liveFuture());

    factory.startAllWatchers();
    currentTimeMillis.set(HEARTBEAT_TIME_MILLIS);

    assertThat(factory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS)).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("terminalFutures")
  void terminalFutureIsUnhealthy(String description, Future<?> future) {
    KubernetesStreamingWatcher watcher = registerWatcher(DynamicKubernetesObject.class, "Pod");
    returnFutureFromThreadPool(future);

    factory.startAllWatchers();
    currentTimeMillis.set(HEARTBEAT_TIME_MILLIS);

    assertThat(future.isDone()).isTrue();
    assertThat(factory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS)).isFalse();
    assertThat(logMessages())
        .anySatisfy(
            message ->
                assertThat(message).contains(ACCOUNT, watcher.watcherId(), "has terminated"));
  }

  @Test
  void unrecordedHeartbeatIsUnhealthyWithoutReportingMisleadingAge() {
    KubernetesStreamingWatcher watcher =
        registerWatcher(DynamicKubernetesObject.class, "Pod", 0, false);
    returnFutureFromThreadPool(liveFuture());
    factory.startAllWatchers();
    currentTimeMillis.set(10_000);

    assertThat(factory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS)).isFalse();
    assertThat(logMessages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(ACCOUNT, watcher.watcherId(), "has not recorded a heartbeat")
                    .doesNotContain("age"));
  }

  @ParameterizedTest
  @ValueSource(longs = {-1_000, 0})
  void recordedZeroAndNegativeHeartbeatOriginsUseTheTimeoutBoundary(long heartbeatTimeMillis) {
    KubernetesStreamingWatcher watcher =
        registerWatcher(DynamicKubernetesObject.class, "Pod", heartbeatTimeMillis);
    returnFutureFromThreadPool(liveFuture());
    factory.startAllWatchers();

    currentTimeMillis.set(heartbeatTimeMillis + LIVENESS_TIMEOUT_MILLIS - 1);
    assertThat(factory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS)).isTrue();

    logAppender.list.clear();
    currentTimeMillis.set(heartbeatTimeMillis + LIVENESS_TIMEOUT_MILLIS);
    assertThat(factory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS)).isFalse();
    assertThat(logMessages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(
                        ACCOUNT, watcher.watcherId(), "heartbeat age 1000 ms", "limit 1000 ms"));
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, 0})
  void livenessTimeoutMustBePositive(long livenessTimeoutMillis) {
    registerWatcher(DynamicKubernetesObject.class, "Pod");
    returnFutureFromThreadPool(liveFuture());
    factory.startAllWatchers();
    currentTimeMillis.set(HEARTBEAT_TIME_MILLIS);

    assertThatThrownBy(() -> factory.allWatchersHealthy(livenessTimeoutMillis))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("liveness timeout must be positive");
  }

  @Test
  void heartbeatFreshnessUsesExclusiveTimeoutBoundaryWithoutOverflow() {
    long heartbeatTime = Long.MAX_VALUE - LIVENESS_TIMEOUT_MILLIS;
    KubernetesStreamingWatcher watcher =
        registerWatcher(DynamicKubernetesObject.class, "Pod", heartbeatTime);
    returnFutureFromThreadPool(liveFuture());
    factory.startAllWatchers();

    currentTimeMillis.set(Long.MAX_VALUE - 1);
    assertThat(factory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS)).isTrue();

    logAppender.list.clear();
    currentTimeMillis.set(Long.MAX_VALUE);
    assertThat(factory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS)).isFalse();
    assertThat(logMessages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(
                        ACCOUNT, watcher.watcherId(), "heartbeat age 1000 ms", "limit 1000 ms"));
  }

  @Test
  void clockRollbackIsUnhealthyAndLoggedSeparately() {
    long heartbeatTime = HEARTBEAT_TIME_MILLIS + 1;
    KubernetesStreamingWatcher watcher =
        registerWatcher(DynamicKubernetesObject.class, "Pod", heartbeatTime);
    returnFutureFromThreadPool(liveFuture());
    factory.startAllWatchers();
    currentTimeMillis.set(HEARTBEAT_TIME_MILLIS);

    assertThat(factory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS)).isFalse();
    assertThat(logMessages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(
                        ACCOUNT,
                        watcher.watcherId(),
                        "ticker rollback",
                        "heartbeat 10001 ms",
                        "current time 10000 ms")
                    .doesNotContain("heartbeat age"));
  }

  @Test
  void negativeTickerRollbackIsUnhealthyAndLoggedSeparately() {
    KubernetesStreamingWatcher watcher = registerWatcher(DynamicKubernetesObject.class, "Pod", -1);
    returnFutureFromThreadPool(liveFuture());
    factory.startAllWatchers();
    currentTimeMillis.set(-2);

    assertThat(factory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS)).isFalse();
    assertThat(logMessages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(
                        ACCOUNT,
                        watcher.watcherId(),
                        "ticker rollback",
                        "heartbeat -1 ms",
                        "current time -2 ms")
                    .doesNotContain("heartbeat age"));
  }

  @Test
  void heartbeatAgeOverflowIsUnhealthyAndLoggedSeparately() {
    KubernetesStreamingWatcher watcher =
        registerWatcher(DynamicKubernetesObject.class, "Pod", Long.MIN_VALUE);
    returnFutureFromThreadPool(liveFuture());
    factory.startAllWatchers();
    currentTimeMillis.set(Long.MAX_VALUE);

    assertThat(factory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS)).isFalse();
    assertThat(logMessages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(ACCOUNT, watcher.watcherId(), "ticker elapsed time overflow")
                    .doesNotContain("heartbeat age"));
  }

  @Test
  void heartbeatRecordedAfterHealthSnapshotDoesNotCreateFalseTickerRollback() throws Exception {
    BlockingTicker ticker = new BlockingTicker(100);
    AtomicLong heartbeatTimeMillis = new AtomicLong(100);
    TestKubernetesStreamingWatcherFactory concurrentFactory =
        new TestKubernetesStreamingWatcherFactory(
            apiClient, ACCOUNT, 200, threadPool, new NoOpStartupConcurrencyControl(), ticker);
    concurrentFactory.addHeartbeat(heartbeatTimeMillis::get, () -> true);
    concurrentFactory.watcherFor(
        DynamicKubernetesObject.class,
        "Pod",
        "",
        "v1",
        "pods",
        mock(State.class),
        new ArrayBlockingQueue<>(1),
        Set.of(),
        1_000,
        20,
        300);
    doReturn(liveFuture()).when(threadPool).submit(any(Runnable.class));
    concurrentFactory.startAllWatchers();
    ExecutorService healthCheck = Executors.newSingleThreadExecutor();
    try {
      ticker.blockNextCallFrom("health-check");
      Future<Boolean> health =
          healthCheck.submit(
              () -> {
                Thread.currentThread().setName("health-check");
                return concurrentFactory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS);
              });
      assertThat(ticker.awaitBlockedCall()).isTrue();

      heartbeatTimeMillis.set(200);
      ticker.releaseBlockedCall();

      assertThat(health.get(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      ticker.releaseBlockedCall();
      healthCheck.shutdownNow();
      assertThat(healthCheck.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void multipleWatchersAreUnhealthyWhenAnyWatcherIsUnhealthy() {
    KubernetesStreamingWatcher podWatcher = registerWatcher(PodApiType.class, "Pod", 0);
    registerWatcher(DeploymentApiType.class, "Deployment");
    returnFuturesFromThreadPool(liveFuture(), liveFuture());
    factory.startAllWatchers();
    currentTimeMillis.set(HEARTBEAT_TIME_MILLIS);

    assertThat(factory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS)).isFalse();
    assertThat(logMessages())
        .anySatisfy(message -> assertThat(message).contains(ACCOUNT, podWatcher.watcherId()));
  }

  @Test
  void healthCheckWaitsForConcurrentStartToPublishFuture() throws Exception {
    CompletableFuture<Void> watcherFuture = liveFuture();
    CountDownLatch submitEntered = new CountDownLatch(1);
    CountDownLatch allowSubmitToReturn = new CountDownLatch(1);
    CountDownLatch healthCallEntered = new CountDownLatch(1);
    registerWatcher(DynamicKubernetesObject.class, "Pod");
    factory.signalHealthCallWith(healthCallEntered);
    currentTimeMillis.set(HEARTBEAT_TIME_MILLIS);
    doAnswer(
            invocation -> {
              submitEntered.countDown();
              assertThat(allowSubmitToReturn.await(5, TimeUnit.SECONDS)).isTrue();
              return watcherFuture;
            })
        .when(threadPool)
        .submit(any(Runnable.class));
    ExecutorService lifecycleCalls = Executors.newFixedThreadPool(2);

    try {
      Future<?> startCall = lifecycleCalls.submit(factory::startAllWatchers);
      assertThat(submitEntered.await(5, TimeUnit.SECONDS)).isTrue();
      Future<Boolean> healthCall =
          lifecycleCalls.submit(() -> factory.allWatchersHealthy(LIVENESS_TIMEOUT_MILLIS));
      assertThat(healthCallEntered.await(5, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> healthCall.get(100, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);

      allowSubmitToReturn.countDown();
      startCall.get(5, TimeUnit.SECONDS);
      assertThat(healthCall.get(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      allowSubmitToReturn.countDown();
      lifecycleCalls.shutdownNow();
      assertThat(lifecycleCalls.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  private KubernetesStreamingWatcher registerWatcher(Class<?> apiTypeClass, String kind) {
    return registerWatcher(apiTypeClass, kind, HEARTBEAT_TIME_MILLIS);
  }

  private KubernetesStreamingWatcher registerWatcher(
      Class<?> apiTypeClass, String kind, long heartbeatTimeMillis) {
    return registerWatcher(apiTypeClass, kind, heartbeatTimeMillis, true);
  }

  private KubernetesStreamingWatcher registerWatcher(
      Class<?> apiTypeClass, String kind, long heartbeatTimeMillis, boolean heartbeatRecorded) {
    factory.addHeartbeatTime(heartbeatTimeMillis, heartbeatRecorded);
    return factory.watcherFor(
        dynamicApiType(apiTypeClass),
        kind,
        "",
        "v1",
        kind + "s",
        mock(State.class),
        new ArrayBlockingQueue<>(1),
        Set.of(),
        1_000,
        20,
        300);
  }

  private void registerThreeWatchers() {
    registerWatcher(PodApiType.class, "Pod");
    registerWatcher(DeploymentApiType.class, "Deployment");
    registerWatcher(ServiceApiType.class, "Service");
  }

  private CompletableFuture<Void> liveFuture() {
    return new CompletableFuture<>();
  }

  private void returnFutureFromThreadPool(Future<?> future) {
    doReturn(future).when(threadPool).submit(any(Runnable.class));
  }

  private void returnFuturesFromThreadPool(Future<?> first, Future<?> second) {
    doReturn(first).doReturn(second).when(threadPool).submit(any(Runnable.class));
  }

  private List<String> logMessages() {
    return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Class<DynamicKubernetesObject> dynamicApiType(Class<?> apiTypeClass) {
    return (Class) apiTypeClass;
  }

  private static Stream<Arguments> terminalFutures() {
    CompletableFuture<Void> exceptionallyCompleted = new CompletableFuture<>();
    exceptionallyCompleted.completeExceptionally(new IllegalStateException("watcher failed"));
    CompletableFuture<Void> cancelled = new CompletableFuture<>();
    cancelled.cancel(true);
    return Stream.of(
        Arguments.of("normally completed", CompletableFuture.completedFuture(null)),
        Arguments.of("exceptionally completed", exceptionallyCompleted),
        Arguments.of("cancelled", cancelled));
  }

  private static final class TestKubernetesStreamingWatcherFactory
      extends KubernetesStreamingWatcherFactory {
    private final Deque<LongSupplier> heartbeatTimes = new ArrayDeque<>();
    private final Deque<BooleanSupplier> heartbeatRecorded = new ArrayDeque<>();
    private LongSupplier lastWatcherClock;
    private CountDownLatch stopCallEntered;
    private CountDownLatch healthCallEntered;

    private TestKubernetesStreamingWatcherFactory(
        ApiClient apiClient,
        String account,
        int paginationSize,
        ExecutorService threadPool,
        StartupConcurrencyControl concurrencyControl) {
      super(apiClient, account, paginationSize, threadPool, concurrencyControl);
    }

    private TestKubernetesStreamingWatcherFactory(
        ApiClient apiClient,
        String account,
        int paginationSize,
        ExecutorService threadPool,
        StartupConcurrencyControl concurrencyControl,
        LongSupplier currentTimeMillis) {
      super(apiClient, account, paginationSize, threadPool, concurrencyControl, currentTimeMillis);
    }

    private void addHeartbeatTime(long heartbeatTimeMillis) {
      addHeartbeatTime(heartbeatTimeMillis, true);
    }

    private void addHeartbeatTime(long heartbeatTimeMillis, boolean recorded) {
      addHeartbeat(() -> heartbeatTimeMillis, () -> recorded);
    }

    private void addHeartbeat(LongSupplier heartbeatTimeMillis, BooleanSupplier recordedHeartbeat) {
      heartbeatTimes.addLast(heartbeatTimeMillis);
      heartbeatRecorded.addLast(recordedHeartbeat);
    }

    private LongSupplier getLastWatcherClock() {
      return lastWatcherClock;
    }

    private void signalStopCallWith(CountDownLatch stopCallEntered) {
      this.stopCallEntered = stopCallEntered;
    }

    private void signalHealthCallWith(CountDownLatch healthCallEntered) {
      this.healthCallEntered = healthCallEntered;
    }

    @Override
    public void stopAllRegisteredWatchers() {
      if (stopCallEntered != null) {
        stopCallEntered.countDown();
      }
      super.stopAllRegisteredWatchers();
    }

    @Override
    boolean allWatchersHealthy(long livenessTimeoutMillis) {
      if (healthCallEntered != null) {
        healthCallEntered.countDown();
      }
      return super.allWatchersHealthy(livenessTimeoutMillis);
    }

    @Override
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
        LongSupplier currentTimeMillis) {
      lastWatcherClock = currentTimeMillis;
      KubernetesStreamingWatcher watcher = mock(KubernetesStreamingWatcher.class);
      LongSupplier heartbeatTimeMillis = heartbeatTimes.removeFirst();
      BooleanSupplier recordedHeartbeat = heartbeatRecorded.removeFirst();
      when(watcher.getLastHeartbeatTimeMillis())
          .thenAnswer(invocation -> heartbeatTimeMillis.getAsLong());
      when(watcher.hasRecordedHeartbeat())
          .thenAnswer(invocation -> recordedHeartbeat.getAsBoolean());
      String apiGroup = group.isBlank() ? version : group + "/" + version;
      when(watcher.watcherId())
          .thenReturn(String.format("Kubernetes Watcher[%s/%s]", apiGroup, kind));
      return watcher;
    }
  }

  private static final class PodApiType {}

  private static final class DeploymentApiType {}

  private static final class ServiceApiType {}

  private static final class BlockingTicker implements LongSupplier {
    private final AtomicLong timeMillis;
    private final CountDownLatch blockedCallEntered = new CountDownLatch(1);
    private final CountDownLatch releaseBlockedCall = new CountDownLatch(1);
    private volatile String blockedThreadName;

    private BlockingTicker(long initialTimeMillis) {
      timeMillis = new AtomicLong(initialTimeMillis);
    }

    @Override
    public long getAsLong() {
      long sampledTimeMillis = timeMillis.get();
      if (Thread.currentThread().getName().equals(blockedThreadName)) {
        blockedThreadName = null;
        blockedCallEntered.countDown();
        try {
          if (!releaseBlockedCall.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to release blocked ticker call");
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while waiting to release ticker call", e);
        }
      }
      return sampledTimeMillis;
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
