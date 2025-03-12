/*
 * Copyright 2024 OpsMx, Inc.
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

package com.netflix.spinnaker.kork.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class RetrySupportTest {

  RetrySupport retrySupport;
  int attemptCounter;

  @BeforeEach
  public void setup() {
    retrySupport = spy(new RetrySupport());
    attemptCounter = 0;

    doNothing().when(retrySupport).sleep(10000);
    doNothing().when(retrySupport).sleep(20000);
    doNothing().when(retrySupport).sleep(40000);
    doNothing().when(retrySupport).sleep(80000);
  }

  @ParameterizedTest(name = "should retry until success or {1} attempts is reached")
  @CsvSource({"3,10,4,'empty'", "11,10,10,'Failed after 10 attempts'"})
  void testRetryFailureWithMaxretries(int fail, int retry, int attempt, String msg) {
    // given
    String exceptionMessage = null;
    int failures = fail;
    int maxRetries = retry;
    int expectedAttempts = attempt;
    String expectedExceptionMessage = msg.equalsIgnoreCase("empty") ? null : msg;

    Supplier fn =
        () -> {
          if (attemptCounter++ < failures) {
            throw new IllegalStateException("Failed after " + attemptCounter + " attempts");
          }
          return null;
        };

    // when
    try {
      retrySupport.retry(fn, maxRetries, Duration.ofMillis(10000), false);
    } catch (Exception e) {
      exceptionMessage = e.getMessage();
    }

    // then
    assertEquals(attemptCounter, expectedAttempts);
    assertEquals(exceptionMessage, expectedExceptionMessage);
  }

  @Test
  void testSleepExponentially() {
    // given
    int failures = 4;
    int maxRetries = 20;
    int expectedAttempts = 5;

    Supplier fn =
        () -> {
          if (attemptCounter++ < failures) {
            throw new IllegalStateException("Failed after " + attemptCounter + " attempts");
          }
          return null;
        };

    // when
    retrySupport.retry(fn, maxRetries, Duration.ofMillis(10000), true);

    // then
    assertEquals(attemptCounter, expectedAttempts);
  }
}
