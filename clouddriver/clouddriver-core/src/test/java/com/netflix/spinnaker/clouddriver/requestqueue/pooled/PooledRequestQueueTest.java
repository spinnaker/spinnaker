/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnaker.clouddriver.requestqueue.pooled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.test.log.MemoryAppender;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

final class PooledRequestQueueTest {
  private static final Logger log = LoggerFactory.getLogger(PooledRequestQueueTest.class);

  DynamicConfigService dynamicConfigService = mock(DynamicConfigService.class);

  @Test
  void shouldExecuteRequests() throws Throwable {
    PooledRequestQueue queue =
        new PooledRequestQueue(dynamicConfigService, new NoopRegistry(), 1000, 1000, 1);

    assertThat(queue.execute("foo", () -> 12345L)).isEqualTo(12345L);
  }

  @Test
  void includesMdcWhenExecutingOperation() throws Throwable {
    // Capture the log messages that our test operation generates
    MemoryAppender memoryAppender = new MemoryAppender(PooledRequestQueueTest.class);

    PooledRequestQueue queue =
        new PooledRequestQueue(dynamicConfigService, new NoopRegistry(), 1000, 1000, 1);

    Callable<Long> testCallable =
        () -> {
          Map<String, String> contextMap = MDC.getCopyOfContextMap();
          log.info("contextMap: {}", contextMap);
          return 12345L;
        };

    // Put something in the MDC here, to see if it makes it into the thread that
    // executes the operation.
    String mdcKey = "myKey";
    String mdcValue = "myValue";
    MDC.put(mdcKey, mdcValue);
    assertThat(queue.execute("foo", testCallable)).isEqualTo(12345L);
    List<String> logMessages = memoryAppender.search(mdcKey + "=" + mdcValue, Level.INFO);
    assertThat(logMessages).hasSize(1);

    // And now clear the MDC and make sure the resulting operation gets the empty MDC.
    MDC.clear();
    assertThat(queue.execute("foo", testCallable)).isEqualTo(12345L);
    List<String> emptyMdcMessages = memoryAppender.search("contextMap: null", Level.INFO);
    assertThat(emptyMdcMessages).hasSize(1);
  }

  @Test
  void timesOutIfRequestDoesNotComplete() {
    PooledRequestQueue queue =
        new PooledRequestQueue(dynamicConfigService, new NoopRegistry(), 5000, 10, 1);

    CountDownLatch block = new CountDownLatch(1);
    assertThatThrownBy(
            () -> {
              try {
                queue.execute(
                    "foo",
                    () -> {
                      block.await();
                      return 12345L;
                    });
              } finally {
                block.countDown();
              }
            })
        .isInstanceOf(PromiseTimeoutException.class);
  }

  @Test
  void timesOutRequestIfDoesNotStartInTime() throws Exception {
    long startTimeout = 50;
    PooledRequestQueue queue =
        new PooledRequestQueue(
            dynamicConfigService, new NoopRegistry(), startTimeout, 5 * startTimeout, 1);

    ExecutorService executor = Executors.newFixedThreadPool(2);

    CountDownLatch blockingJobStarted = new CountDownLatch(1);
    CountDownLatch testJobExited = new CountDownLatch(1);

    // Block the queue with a job that holds onto the only executor slot until our test job
    // has exited.
    executor.submit(
        safeRun(
            () -> {
              queue.execute(
                  "foo",
                  () -> {
                    blockingJobStarted.countDown();
                    testJobExited.await();
                    return null;
                  });
            }));

    // Submit another job to the queue, and ensure that it is rejected before starting.
    AtomicBoolean testJobRan = new AtomicBoolean(false);
    CountDownLatch testJobQueued = new CountDownLatch(1);
    Future<Void> testJob =
        executor.submit(
            safeRun(
                () -> {
                  try {
                    blockingJobStarted.await();
                    testJobQueued.countDown();
                    queue.execute(
                        "foo",
                        () -> {
                          testJobRan.set(true);
                          return null;
                        });
                  } finally {
                    testJobExited.countDown();
                  }
                }));

    executor.shutdown();

    // Once the test job is queued, we'll wait a few times the startup timeout for it to finish.
    testJobQueued.await();
    if (!executor.awaitTermination(10 * startTimeout, TimeUnit.MILLISECONDS)) {
      executor.shutdownNow();
      // Fail the test immediately rather than assert on the status of the jobs, given that we
      // interrupted them abnormally.
      fail("Timeout waiting for queued jobs to finish.");
    }

    assertThatThrownBy(testJob::get).hasCauseInstanceOf(PromiseNotStartedException.class);
    assertThat(testJobRan.get()).isFalse();
  }

  /**
   * Translates a {@link ThrowingRunnable} into a {@link Callable<Void>}.
   *
   * <p>Invoking the {@link Callable} calls {@link ThrowingRunnable#run()}. Any {@link Exception}
   * that is thrown is propagated, and any non-{@link Exception} {@link Throwable} is wrapped in a
   * {@link RuntimeException}.
   */
  private static Callable<Void> safeRun(ThrowingRunnable throwingRunnable) {
    return () -> {
      try {
        throwingRunnable.run();
      } catch (Exception e) {
        throw e;
      } catch (Throwable t) {
        throw new RuntimeException(t);
      }
      return null;
    };
  }

  /** A {@link Runnable} that allows an arbitrary {@link Throwable} to be thrown. */
  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Throwable;
  }
}
