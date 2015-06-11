package com.netflix.spinnaker.kato.data.task

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
