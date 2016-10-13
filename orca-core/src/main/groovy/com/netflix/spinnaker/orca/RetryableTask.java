package com.netflix.spinnaker.orca;

/**
 * A retryable task defines its backoff period (the period between delays) and its timeout (the total period of the task)
 */
public interface RetryableTask extends Task {
  long getBackoffPeriod();

  long getTimeout();
}
