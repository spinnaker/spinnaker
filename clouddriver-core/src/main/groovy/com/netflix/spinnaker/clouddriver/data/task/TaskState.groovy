package com.netflix.spinnaker.clouddriver.data.task

enum TaskState {
  STARTED,
  COMPLETED,
  FAILED

  boolean isCompleted() {
    this != STARTED
  }

  boolean isFailed() {
    this == FAILED
  }
}
