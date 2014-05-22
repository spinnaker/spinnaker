/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.bluespar.kato.data.task

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.Immutable
import groovy.transform.ToString

import java.util.logging.Logger

public class DefaultTask implements Task {
  private static final Logger log = Logger.getLogger(this.class.name)

  final String id
  final List<Status> history = []
  final List<Object> resultObjects = []
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