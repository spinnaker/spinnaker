package com.netflix.kato.data.task

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.Immutable
import groovy.transform.ToString
import groovy.util.logging.Log4j

@Log4j
public class DefaultTask implements Task {
  final String id
  final List<Status> history = []
  final long startTimeMs = new Date().time

  private String phase
  private String status
  private Boolean complete = Boolean.FALSE
  private Boolean failed = Boolean.FALSE

  public DefaultTask(final String id, String phase, String status) {
    this.id = id
    updateStatus phase, status
  }

  public void updateStatus(String phase, String status) {
    if (complete) {
      throw new IllegalStateException("Task is already completed! No further updates allowed!")
    }
    log.info "[$phase] - $status"
    this.phase = phase
    this.status = status
    history << new TaskDisplayStatus(phase, status)
  }

  public void complete() {
    if (complete) {
      throw new IllegalStateException("Task is already completed! No further updates allowed!")
    }
    this.complete = Boolean.TRUE
  }

  public void fail() {
    if (complete) {
      throw new IllegalStateException("Task is already completed! No further updates allowed!")
    }
    this.failed = Boolean.TRUE
    complete()
  }

  public Status getStatus() {
    new DefaultTaskStatus(phase, status, complete, failed)
  }

  public String toString() {
    getStatus().toString()
  }

  @ToString
  static class TaskDisplayStatus implements Status {
    final String phase
    final String status

    TaskDisplayStatus(String phase, String status) {
      this.phase = phase
      this.status = status
    }

    @JsonIgnore
    Boolean complete
    @JsonIgnore
    Boolean failed
    @JsonIgnore
    Boolean isCompleted() { complete }
    @JsonIgnore
    Boolean isFailed() { failed }
  }

  @ToString
  @Immutable
  static class DefaultTaskStatus implements Status {
    String phase
    String status
    Boolean complete
    Boolean failed

    Boolean isCompleted() { complete }
    Boolean isFailed() { failed }
  }

}