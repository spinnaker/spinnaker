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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Circuit breaker protecting the scheduler from cascading failures.
 *
 * <p>Responsibilities: - Track failures and transition between CLOSED -> OPEN -> HALF_OPEN ->
 * CLOSED - Enforce cooldown and half-open probe windows - Emit simple counters for allowed/blocked
 * requests
 *
 * <p>Non-responsibilities: - Redis time sourcing (uses System clock) - Scheduling logic (callers
 * use this as a guard)
 */
@Slf4j
public class PrioritySchedulerCircuitBreaker {

  public enum State {
    CLOSED, // Normal operation
    OPEN, // Circuit tripped, blocking requests
    HALF_OPEN // Testing if service recovered
  }

  private final String name;
  private final int failureThreshold;
  private final long timeWindowMs;
  private final long cooldownMs;
  private final long halfOpenDurationMs;
  private final PrioritySchedulerMetrics metrics;

  // Circuit breaker state
  private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
  private final AtomicLong stateChangeTimeMs = new AtomicLong(System.currentTimeMillis());
  private final AtomicLong windowStartMs = new AtomicLong(System.currentTimeMillis());

  // Failure tracking
  private final AtomicInteger failureCount = new AtomicInteger(0);
  private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
  private final AtomicInteger halfOpenAttempts = new AtomicInteger(0);

  // Statistics
  private final LongAdder totalAllowed = new LongAdder();
  private final LongAdder totalBlocked = new LongAdder();
  private final AtomicLong lastFailureMs = new AtomicLong(0);

  /** Create a circuit breaker with default settings suitable for Redis operations. */
  public PrioritySchedulerCircuitBreaker(String name, PrioritySchedulerMetrics metrics) {
    this(name, 5, 10000, 30000, 5000, metrics);
  }

  /**
   * Create a circuit breaker with custom settings.
   *
   * @param name Circuit breaker name for logging/metrics
   * @param failureThreshold Number of failures to trip the circuit
   * @param timeWindowMs Time window for counting failures (ms)
   * @param cooldownMs Time to wait in OPEN state before trying HALF_OPEN (ms)
   * @param halfOpenDurationMs Duration of HALF_OPEN test period (ms)
   * @param metrics Metrics registry for tracking circuit breaker stats
   */
  public PrioritySchedulerCircuitBreaker(
      String name,
      int failureThreshold,
      long timeWindowMs,
      long cooldownMs,
      long halfOpenDurationMs,
      PrioritySchedulerMetrics metrics) {
    this.name = name;
    this.failureThreshold = failureThreshold;
    this.timeWindowMs = timeWindowMs;
    this.cooldownMs = cooldownMs;
    this.halfOpenDurationMs = halfOpenDurationMs;
    this.metrics = metrics != null ? metrics : PrioritySchedulerMetrics.NOOP;
  }

  /**
   * Check if a request should be allowed through the circuit breaker.
   *
   * @return true if the request is allowed, false if blocked
   */
  public boolean allowRequest() {
    State currentState = state.get();
    long now = System.currentTimeMillis();

    switch (currentState) {
      case CLOSED:
        // Check if we should reset the failure window
        if (now - windowStartMs.get() > timeWindowMs) {
          resetFailureWindow(now);
        }
        totalAllowed.increment();
        return true;

      case OPEN:
        // Check if cooldown period has expired
        if (now - stateChangeTimeMs.get() >= cooldownMs) {
          if (transitionToHalfOpen(now)) {
            log.info(
                "Circuit breaker '{}' transitioning from OPEN to HALF_OPEN after cooldown", name);
            totalAllowed.increment();
            return true; // Allow one probe request
          }
        }
        totalBlocked.increment();
        metrics.recordCircuitBreakerBlocked(name);
        return false;

      case HALF_OPEN:
        // Allow limited requests during probe period
        if (now - stateChangeTimeMs.get() < halfOpenDurationMs) {
          int attempts = halfOpenAttempts.incrementAndGet();
          if (attempts <= 3) { // Allow up to 3 probe attempts
            totalAllowed.increment();
            return true;
          }
        } else {
          // Half-open period expired without enough successes, go back to open
          transitionToOpen(now, "Half-open period expired without recovery");
        }
        totalBlocked.increment();
        metrics.recordCircuitBreakerBlocked(name);
        return false;

      default:
        log.error("Circuit breaker '{}' in unknown state: {}", name, currentState);
        return false;
    }
  }

  /** Record a successful operation. */
  public void recordSuccess() {
    State currentState = state.get();
    consecutiveFailures.set(0);

    if (currentState == State.HALF_OPEN) {
      if (transitionToClosed(System.currentTimeMillis())) {
        log.info("Circuit breaker '{}' recovered, transitioning from HALF_OPEN to CLOSED", name);
        metrics.recordCircuitBreakerRecovery(name);
      }
    }
  }

  /**
   * Record a failed operation.
   *
   * @param exception The exception that caused the failure
   */
  public void recordFailure(Exception exception) {
    long now = System.currentTimeMillis();
    lastFailureMs.set(now);

    int consecutive = consecutiveFailures.incrementAndGet();
    State currentState = state.get();

    switch (currentState) {
      case CLOSED:
        // Check if we should reset the window
        if (now - windowStartMs.get() > timeWindowMs) {
          resetFailureWindow(now);
        }

        int failures = failureCount.incrementAndGet();
        if (failures >= failureThreshold || consecutive >= failureThreshold) {
          String reason =
              String.format(
                  "threshold exceeded (failures=%d, consecutive=%d, threshold=%d)",
                  failures, consecutive, failureThreshold);
          if (transitionToOpen(now, reason)) {
            log.warn("Circuit breaker '{}' tripped: {}", name, reason, exception);
            metrics.recordCircuitBreakerTrip(name, exception.getClass().getSimpleName());
          }
        }
        break;

      case HALF_OPEN:
        // Probe failed, go back to open
        String reason = "probe failed during half-open";
        if (transitionToOpen(now, reason)) {
          log.warn("Circuit breaker '{}' probe failed, returning to OPEN state", name, exception);
          metrics.recordCircuitBreakerTrip(name, "probe_failure");
        }
        break;

      case OPEN:
        // Already open, just track the failure
        break;
    }
  }

  /** Get the current state of the circuit breaker. */
  public State getState() {
    return state.get();
  }

  /** Get human-readable status for monitoring/debugging. */
  public String getStatus() {
    State currentState = state.get();
    long now = System.currentTimeMillis();
    long stateAge = now - stateChangeTimeMs.get();

    switch (currentState) {
      case CLOSED:
        return String.format(
            "CLOSED (failures=%d/%d in window)", failureCount.get(), failureThreshold);
      case OPEN:
        long remaining = Math.max(0, cooldownMs - stateAge);
        return String.format("OPEN (cooldown remaining=%dms)", remaining);
      case HALF_OPEN:
        return String.format("HALF_OPEN (attempts=%d, age=%dms)", halfOpenAttempts.get(), stateAge);
      default:
        return "UNKNOWN";
    }
  }

  /** Force the circuit breaker to close (for testing/recovery). */
  public void reset() {
    long now = System.currentTimeMillis();
    state.set(State.CLOSED);
    stateChangeTimeMs.set(now);
    resetFailureWindow(now);
    consecutiveFailures.set(0);
    halfOpenAttempts.set(0);
    log.info("Circuit breaker '{}' manually reset to CLOSED state", name);
  }

  /** Get statistics for monitoring. */
  public CircuitBreakerStats getStats() {
    return new CircuitBreakerStats(
        name,
        state.get(),
        failureCount.get(),
        consecutiveFailures.get(),
        totalAllowed.sum(),
        totalBlocked.sum(),
        System.currentTimeMillis() - stateChangeTimeMs.get(),
        lastFailureMs.get());
  }

  // State transition helpers

  private boolean transitionToOpen(long now, String reason) {
    if (state.compareAndSet(State.CLOSED, State.OPEN)
        || state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
      stateChangeTimeMs.set(now);
      halfOpenAttempts.set(0);
      return true;
    }
    return false;
  }

  private boolean transitionToHalfOpen(long now) {
    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
      stateChangeTimeMs.set(now);
      halfOpenAttempts.set(0);
      resetFailureWindow(now);
      return true;
    }
    return false;
  }

  private boolean transitionToClosed(long now) {
    if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
      stateChangeTimeMs.set(now);
      resetFailureWindow(now);
      halfOpenAttempts.set(0);
      return true;
    }
    return false;
  }

  private void resetFailureWindow(long now) {
    windowStartMs.set(now);
    failureCount.set(0);
  }

  /**
   * Statistics holder for circuit breaker monitoring.
   *
   * <p>Fields:
   *
   * <ul>
   *   <li>{@code name} - circuit breaker name
   *   <li>{@code state} - current circuit breaker state
   *   <li>{@code failureCount} - failures in current window
   *   <li>{@code consecutiveFailures} - consecutive failures without success
   *   <li>{@code totalAllowed} - total requests allowed through
   *   <li>{@code totalBlocked} - total requests blocked
   *   <li>{@code stateAgeMs} - time in current state (milliseconds)
   *   <li>{@code lastFailureMs} - timestamp of last failure (milliseconds since epoch)
   * </ul>
   */
  @Getter
  @RequiredArgsConstructor
  public static class CircuitBreakerStats {
    private final String name;
    private final State state;
    private final int failureCount;
    private final int consecutiveFailures;
    private final long totalAllowed;
    private final long totalBlocked;
    private final long stateAgeMs;
    private final long lastFailureMs;

    @Override
    public String toString() {
      return String.format(
          "CircuitBreaker[%s]: state=%s, failures=%d, consecutive=%d, allowed=%d, blocked=%d, age=%dms",
          name, state, failureCount, consecutiveFailures, totalAllowed, totalBlocked, stateAgeMs);
    }
  }
}
