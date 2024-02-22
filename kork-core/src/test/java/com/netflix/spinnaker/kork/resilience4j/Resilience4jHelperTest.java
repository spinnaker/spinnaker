/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.kork.resilience4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.netflix.spinnaker.kork.test.log.MemoryAppender;
import io.github.resilience4j.retry.MaxRetriesExceededException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Resilience4jHelperTest {

  private static final Logger log = LoggerFactory.getLogger(Resilience4jHelperTest.class);

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void testConfigureLogging() {
    // Verify that retries configured with Resilience4jHelper.configureLogging
    // log the expected messages.

    // given:
    MemoryAppender memoryAppender = new MemoryAppender(Resilience4jHelperTest.class);

    RetryRegistry retryRegistry = RetryRegistry.ofDefaults();
    String description = "test retry description";
    Resilience4jHelper.configureLogging(retryRegistry, description, log);

    String retryName = "test retry name";
    Retry retry = retryRegistry.retry(retryName);

    RuntimeException exception = new RuntimeException("retryable exception");

    // when:
    assertThatThrownBy(
            () ->
                retry.executeRunnable(
                    () -> {
                      throw exception;
                    }))
        .isEqualTo(exception);

    // then:
    assertThat(
            memoryAppender.search(
                description + " retries configured for: " + retryName, Level.INFO))
        .hasSize(1);
    assertThat(
            memoryAppender.search(
                "Retrying "
                    + description
                    + " for "
                    + retryName
                    + ". Attempt #1 failed with exception: "
                    + exception.toString(),
                Level.INFO))
        .hasSize(1);
    assertThat(
            memoryAppender.search(
                "Retrying "
                    + description
                    + " for "
                    + retryName
                    + ". Attempt #2 failed with exception: "
                    + exception.toString(),
                Level.INFO))
        .hasSize(1);
    assertThat(
            memoryAppender.search(
                description
                    + " for "
                    + retryName
                    + " failed after 3 attempts. Exception: "
                    + exception.toString(),
                Level.ERROR))
        .hasSize(1);
  }

  @Test
  void testHandleNullLastThrowableRetryOnRetryAndSuccess() {

    // given:
    MemoryAppender memoryAppender = new MemoryAppender(Resilience4jHelperTest.class);

    RetryConfig.Builder<String> retryConfigBuilder = RetryConfig.custom();

    // returning true means retry.  retry once, and then don't retry the next
    // time to exercise logging for both RetryOnRetryEvent and
    // RetryOnSuccessEvent.  Use an AtomicBoolean so the predicate can modify it.
    AtomicBoolean retried = new AtomicBoolean();

    retryConfigBuilder.retryOnResult(
        (String string) -> {
          if (retried.compareAndSet(false, true)) {
            return true;
          }
          return false;
        });

    RetryConfig retryConfig = retryConfigBuilder.build();
    RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);

    String description = "test retry description";
    Resilience4jHelper.configureLogging(retryRegistry, description, log);

    String retryName = "handle null lastThrowable";
    Retry retry = retryRegistry.retry(retryName);

    // when:
    String result = retry.executeSupplier(() -> "result");

    // then:
    assertThat(result).isEqualTo("result");

    assertThat(
            memoryAppender.search(
                "Retrying "
                    + description
                    + " for "
                    + retryName
                    + ". Attempt #1 failed with exception: null",
                Level.INFO))
        .hasSize(1);

    assertThat(
            memoryAppender.search(
                description
                    + " for "
                    + retryName
                    + " is now successful in attempt #2. "
                    + "Last attempt had failed with exception: null",
                Level.INFO))
        .hasSize(1);
  }

  @Test
  void testHandleNullLastThrowableRetryOnError() {

    // given:
    MemoryAppender memoryAppender = new MemoryAppender(Resilience4jHelperTest.class);

    RetryConfig.Builder<String> retryConfigBuilder = RetryConfig.custom();

    // returning true means retry.  retry every time to exercise behavior when
    // attempts are exhausted.
    retryConfigBuilder.retryOnResult((String string) -> true);

    // Whether failAfterMaxAttempts is true or false, lastThrowable is either
    // MaxRetriesExceeded or MaxRetriesExceededException in RetryOnErrorEvent
    // (i.e. it isn't null).  So, either we need a different test, or the code
    // that handles RetryOnErrorEvent can assume lastThrowable isn't null.  It
    // still seems safer to handle it since it's straightforward, and since
    // lastThrowable has a @Nullable annotation in AbstractRetryEvent.
    retryConfigBuilder.failAfterMaxAttempts(true);

    RetryConfig retryConfig = retryConfigBuilder.build();
    RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);

    String description = "test retry description";
    Resilience4jHelper.configureLogging(retryRegistry, description, log);

    String retryName = "handle null lastThrowable";
    Retry retry = retryRegistry.retry(retryName);

    // when:
    assertThatThrownBy(() -> retry.executeSupplier(() -> "result"))
        .isInstanceOf(MaxRetriesExceededException.class);

    // then:
    assertThat(
            memoryAppender.search(
                "Retrying "
                    + description
                    + " for "
                    + retryName
                    + ". Attempt #1 failed with exception: null",
                Level.INFO))
        .hasSize(1);

    assertThat(
            memoryAppender.search(
                "Retrying "
                    + description
                    + " for "
                    + retryName
                    + ". Attempt #2 failed with exception: null",
                Level.INFO))
        .hasSize(1);

    assertThat(
            memoryAppender.search(
                description
                    + " for "
                    + retryName
                    + " failed after "
                    + retryConfig.getMaxAttempts()
                    + " attempts. Exception: io.github.resilience4j.retry.MaxRetriesExceeded: max retries is reached out for the result predicate check",
                Level.ERROR))
        .hasSize(1);

    assertThat(
            memoryAppender.search(
                description
                    + " for "
                    + retryName
                    + " failed after "
                    + (retryConfig.getMaxAttempts() + 1)
                    + " attempts. Exception: io.github.resilience4j.retry.MaxRetriesExceededException: Retry '"
                    + retryName
                    + "' has exhausted all attempts ("
                    + retryConfig.getMaxAttempts()
                    + ")",
                Level.ERROR))
        .hasSize(1);
  }
}
