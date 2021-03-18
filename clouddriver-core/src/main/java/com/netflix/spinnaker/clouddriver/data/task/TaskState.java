package com.netflix.spinnaker.clouddriver.data.task;

public enum TaskState {
  STARTED,
  COMPLETED,
  FAILED,
  FAILED_RETRYABLE;

  public boolean isCompleted() {
    return !this.equals(STARTED);
  }

  public boolean isFailed() {
    return this.equals(FAILED) || this.equals(FAILED_RETRYABLE);
  }

  public boolean isRetryable() {
    return this.equals(FAILED_RETRYABLE);
  }
}
