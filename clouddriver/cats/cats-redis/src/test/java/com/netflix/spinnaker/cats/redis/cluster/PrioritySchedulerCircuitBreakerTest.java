/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.cats.redis.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Test suite for PrioritySchedulerCircuitBreaker component.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Initial state verification (circuit starts CLOSED)
 *   <li>State transitions: CLOSED -> OPEN -> HALF_OPEN -> CLOSED/OPEN
 *   <li>Failure threshold behavior (trips after N failures)
 *   <li>Cooldown period timing (OPEN -> HALF_OPEN transition)
 *   <li>Recovery via successful probe requests
 *   <li>Probe failure handling (HALF_OPEN -> OPEN)
 *   <li>Manual reset for administrative control
 *   <li>Request blocking and metrics recording
 *   <li>Status messages and statistics tracking
 * </ul>
 *
 * <p><b>Metrics Verified:</b>
 *
 * <ul>
 *   <li>{@code cats.priorityScheduler.circuitBreaker.trip} - recorded when circuit trips to OPEN
 *   <li>{@code cats.priorityScheduler.circuitBreaker.blocked} - recorded for each blocked request
 *   <li>{@code cats.priorityScheduler.circuitBreaker.recovery} - recorded when circuit recovers to
 *       CLOSED
 * </ul>
 *
 * <p><b>Note:</b> Tests use Thread.sleep() for timing-based state transitions. This is appropriate
 * because circuit breaker transitions are inherently time-based (cooldown periods). Condition-based
 * polling cannot replace the actual passage of time required for OPEN -> HALF_OPEN transitions.
 */
@DisplayName("PrioritySchedulerCircuitBreaker Tests")
public class PrioritySchedulerCircuitBreakerTest {

  private Registry registry;
  private PrioritySchedulerMetrics metrics;
  private PrioritySchedulerCircuitBreaker circuitBreaker;

  @BeforeEach
  public void setUp() {
    registry = new DefaultRegistry();
    metrics = new PrioritySchedulerMetrics(registry);
    circuitBreaker =
        new PrioritySchedulerCircuitBreaker(
            "test", 3, // 3 failures to trip
            5000, // 5 second window
            1000, // 1 second cooldown for testing
            500, // 0.5 second half-open
            metrics);
  }

  /** Tests that a newly created circuit breaker starts in CLOSED state and allows requests. */
  @Test
  @Timeout(5)
  @DisplayName("Should start in CLOSED state and allow requests")
  public void testCircuitBreakerStartsClosed() {
    // Verify initial state is CLOSED (critical for circuit breaker behavior)
    assertThat(circuitBreaker.getState()).isEqualTo(PrioritySchedulerCircuitBreaker.State.CLOSED);
    // Verify requests are allowed when CLOSED
    assertThat(circuitBreaker.allowRequest()).isTrue();
  }

  /**
   * Tests that circuit breaker transitions from CLOSED to OPEN after reaching the failure
   * threshold. Verifies state remains CLOSED for failures below threshold, transitions to OPEN at
   * threshold, blocks requests when OPEN, and records the trip metric.
   */
  @Test
  @Timeout(5)
  @DisplayName("Should trip to OPEN state after reaching failure threshold")
  public void testCircuitBreakerTripsAfterThreshold() {
    // Given: Circuit is CLOSED and allowing requests
    assertThat(circuitBreaker.allowRequest()).isTrue();

    // When: Record failures below threshold (1 of 3)
    JedisConnectionException error = new JedisConnectionException("Connection failed");
    circuitBreaker.recordFailure(error);
    // Then: State remains CLOSED, requests still allowed
    assertThat(circuitBreaker.getState()).isEqualTo(PrioritySchedulerCircuitBreaker.State.CLOSED);
    assertThat(circuitBreaker.allowRequest()).isTrue();

    // When: Record second failure (2 of 3)
    circuitBreaker.recordFailure(error);
    // Then: State still CLOSED, requests still allowed
    assertThat(circuitBreaker.getState()).isEqualTo(PrioritySchedulerCircuitBreaker.State.CLOSED);
    assertThat(circuitBreaker.allowRequest()).isTrue();

    // When: Record third failure (threshold reached)
    circuitBreaker.recordFailure(error);
    // Then: Circuit trips to OPEN, requests blocked (protects system from cascading failures)
    assertThat(circuitBreaker.getState()).isEqualTo(PrioritySchedulerCircuitBreaker.State.OPEN);
    assertThat(circuitBreaker.allowRequest()).isFalse();

    // Verify trip metric recorded with exception class as reason tag
    assertThat(
            registry
                .counter(
                    registry
                        .createId("cats.priorityScheduler.circuitBreaker.trip")
                        .withTag("scheduler", "priority")
                        .withTag("name", "test")
                        .withTag("reason", "JedisConnectionException"))
                .count())
        .isEqualTo(1);
  }

  /**
   * Tests that circuit breaker transitions from OPEN to HALF_OPEN after the cooldown period
   * expires. Verifies requests are blocked while OPEN, then allowed after cooldown with state
   * change to HALF_OPEN.
   */
  @Test
  @Timeout(5)
  @DisplayName("Should transition from OPEN to HALF_OPEN after cooldown period")
  public void testCircuitBreakerTransitionsToHalfOpen() throws InterruptedException {
    // Given: Circuit is tripped to OPEN state
    JedisConnectionException error = new JedisConnectionException("Connection failed");
    for (int i = 0; i < 3; i++) {
      circuitBreaker.recordFailure(error);
    }
    assertThat(circuitBreaker.getState()).isEqualTo(PrioritySchedulerCircuitBreaker.State.OPEN);
    // Verify requests blocked while OPEN
    assertThat(circuitBreaker.allowRequest()).isFalse();

    // When: Wait for cooldown period to expire (1100ms > 1000ms cooldown)
    Thread.sleep(1100);

    // Then: Transitions to HALF_OPEN and allows probe request (critical for recovery)
    assertThat(circuitBreaker.allowRequest()).isTrue();
    assertThat(circuitBreaker.getState())
        .isEqualTo(PrioritySchedulerCircuitBreaker.State.HALF_OPEN);
  }

  /**
   * Tests that circuit breaker recovers from OPEN to CLOSED after a successful probe during
   * HALF_OPEN state. Verifies the full recovery path: OPEN -> wait for cooldown -> HALF_OPEN ->
   * recordSuccess -> CLOSED. Also verifies the recovery metric is recorded.
   */
  @Test
  @Timeout(5)
  @DisplayName("Should recover from HALF_OPEN to CLOSED after successful probe")
  public void testCircuitBreakerRecovery() throws InterruptedException {
    // Given: Circuit is tripped to OPEN state
    JedisConnectionException error = new JedisConnectionException("Connection failed");
    for (int i = 0; i < 3; i++) {
      circuitBreaker.recordFailure(error);
    }
    assertThat(circuitBreaker.getState()).isEqualTo(PrioritySchedulerCircuitBreaker.State.OPEN);

    // When: Wait for cooldown and transition to HALF_OPEN
    Thread.sleep(1100);
    assertThat(circuitBreaker.allowRequest()).isTrue();
    assertThat(circuitBreaker.getState())
        .isEqualTo(PrioritySchedulerCircuitBreaker.State.HALF_OPEN);

    // When: Record successful probe request
    circuitBreaker.recordSuccess();

    // Then: Circuit recovers to CLOSED (critical for system resilience)
    assertThat(circuitBreaker.getState()).isEqualTo(PrioritySchedulerCircuitBreaker.State.CLOSED);
    assertThat(circuitBreaker.allowRequest()).isTrue();

    // Verify recovery metric recorded
    assertThat(
            registry
                .counter(
                    registry
                        .createId("cats.priorityScheduler.circuitBreaker.recovery")
                        .withTag("scheduler", "priority")
                        .withTag("name", "test"))
                .count())
        .isEqualTo(1);
  }

  /**
   * Tests that circuit breaker returns to OPEN state when a probe request fails during HALF_OPEN
   * state. Verifies the failure path: OPEN -> wait for cooldown -> HALF_OPEN -> recordFailure ->
   * OPEN with requests blocked.
   */
  @Test
  @Timeout(5)
  @DisplayName("Should return to OPEN when probe fails during HALF_OPEN state")
  public void testCircuitBreakerHalfOpenFailureReturnsToOpen() throws InterruptedException {
    // Given: Circuit is tripped to OPEN state
    JedisConnectionException error = new JedisConnectionException("Connection failed");
    for (int i = 0; i < 3; i++) {
      circuitBreaker.recordFailure(error);
    }

    // When: Wait for cooldown and transition to HALF_OPEN
    Thread.sleep(1100);
    assertThat(circuitBreaker.allowRequest()).isTrue();
    assertThat(circuitBreaker.getState())
        .isEqualTo(PrioritySchedulerCircuitBreaker.State.HALF_OPEN);

    // When: Probe request fails
    circuitBreaker.recordFailure(error);

    // Then: Circuit returns to OPEN (critical for resilience - service still unhealthy)
    assertThat(circuitBreaker.getState()).isEqualTo(PrioritySchedulerCircuitBreaker.State.OPEN);
    assertThat(circuitBreaker.allowRequest()).isFalse();
  }

  /**
   * Tests that circuit breaker can be manually reset from OPEN to CLOSED state. Verifies reset()
   * immediately closes the circuit and allows requests.
   */
  @Test
  @Timeout(5)
  @DisplayName("Should reset from OPEN to CLOSED state via manual reset")
  public void testCircuitBreakerReset() {
    // Given: Circuit is tripped to OPEN state
    JedisConnectionException error = new JedisConnectionException("Connection failed");
    for (int i = 0; i < 3; i++) {
      circuitBreaker.recordFailure(error);
    }
    assertThat(circuitBreaker.getState()).isEqualTo(PrioritySchedulerCircuitBreaker.State.OPEN);

    // When: Administrative reset is invoked
    circuitBreaker.reset();

    // Then: Circuit immediately closes (critical for administrative control/recovery)
    assertThat(circuitBreaker.getState()).isEqualTo(PrioritySchedulerCircuitBreaker.State.CLOSED);
    assertThat(circuitBreaker.allowRequest()).isTrue();
  }

  /**
   * Tests that circuit breaker blocks all requests when in OPEN state. Verifies multiple
   * consecutive requests are blocked and the blocked metric is recorded for each.
   */
  @Test
  @Timeout(5)
  @DisplayName("Should block all requests and record metrics when OPEN")
  public void testCircuitBreakerBlocksRequestsWhenOpen() {
    // Given: Circuit is tripped to OPEN state
    JedisConnectionException error = new JedisConnectionException("Connection failed");
    for (int i = 0; i < 3; i++) {
      circuitBreaker.recordFailure(error);
    }

    // When: Multiple requests arrive while OPEN
    for (int i = 0; i < 10; i++) {
      // Then: All requests blocked (critical for protecting system from cascading failures)
      assertThat(circuitBreaker.allowRequest()).isFalse();
    }

    // Verify blocked metric recorded for each blocked request
    assertThat(
            registry
                .counter(
                    registry
                        .createId("cats.priorityScheduler.circuitBreaker.blocked")
                        .withTag("scheduler", "priority")
                        .withTag("name", "test"))
                .count())
        .isEqualTo(10);
  }

  /**
   * Tests that getStatus() returns accurate human-readable status messages. Verifies status
   * includes state name, failure count in CLOSED state, and cooldown remaining in OPEN state.
   */
  @Test
  @Timeout(5)
  @DisplayName("Should return accurate human-readable status messages")
  public void testCircuitBreakerStatusMessages() {
    // Verify CLOSED status shows failures/threshold (critical for monitoring)
    assertThat(circuitBreaker.getStatus()).contains("CLOSED").contains("0/3");

    // Record some failures and verify count updates
    JedisConnectionException error = new JedisConnectionException("Connection failed");
    circuitBreaker.recordFailure(error);
    circuitBreaker.recordFailure(error);
    assertThat(circuitBreaker.getStatus()).contains("CLOSED").contains("2/3");

    // Trip the circuit and verify OPEN status shows cooldown info
    circuitBreaker.recordFailure(error);
    assertThat(circuitBreaker.getStatus()).contains("OPEN").contains("cooldown remaining");
  }

  /**
   * Tests that getStats() returns accurate statistics. Verifies name, state, failure count, total
   * allowed, and total blocked are tracked correctly through a sequence of operations.
   */
  @Test
  @Timeout(5)
  @DisplayName("Should track and return accurate statistics")
  public void testCircuitBreakerStatistics() {
    // Given: Allow 5 requests while CLOSED
    for (int i = 0; i < 5; i++) {
      assertThat(circuitBreaker.allowRequest()).isTrue();
    }

    // When: Trip the circuit with 3 failures
    JedisConnectionException error = new JedisConnectionException("Connection failed");
    for (int i = 0; i < 3; i++) {
      circuitBreaker.recordFailure(error);
    }

    // When: Block 3 requests while OPEN
    for (int i = 0; i < 3; i++) {
      assertThat(circuitBreaker.allowRequest()).isFalse();
    }

    // Then: Statistics accurately reflect all operations (critical for monitoring/debugging)
    PrioritySchedulerCircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
    assertThat(stats.getName()).isEqualTo("test");
    assertThat(stats.getState()).isEqualTo(PrioritySchedulerCircuitBreaker.State.OPEN);
    assertThat(stats.getFailureCount()).isEqualTo(3);
    assertThat(stats.getTotalAllowed()).isEqualTo(5);
    assertThat(stats.getTotalBlocked()).isEqualTo(3);
  }
}
