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

package com.netflix.spinnaker.clouddriver.data.task

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.clouddriver.core.ClouddriverHostname
import groovy.transform.CompileStatic
import groovy.transform.Immutable

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.logging.Logger

@CompileStatic
public class DefaultTask implements Task {
  private static final Logger log = Logger.getLogger(DefaultTask.name)

  final String id
  final String ownerId = ClouddriverHostname.ID
  private final Deque<Status> statusHistory = new ConcurrentLinkedDeque<Status>()
  private final Deque<Object> resultObjects = new ConcurrentLinkedDeque<Object>()
  final long startTimeMs = System.currentTimeMillis()

  public String getOwnerId() {
    return ownerId
  }

  public DefaultTask(String id) {
    this(id, 'INIT', "Creating task ${id}")
  }

  public DefaultTask(String id, String phase, String status) {
    def initialStatus = new DefaultTaskStatus(phase, status, TaskState.STARTED)
    statusHistory.addLast(initialStatus)
    this.id = id
  }

  public void updateStatus(String phase, String status) {
    statusHistory.addLast(currentStatus().update(phase, status))
    log.info "[$phase] - $status"
  }

  public void complete() {
    statusHistory.addLast(currentStatus().update(TaskState.COMPLETED))
  }

  public List<? extends Status> getHistory() {
    statusHistory.collect { new TaskDisplayStatus(it) }
  }

  public void fail() {
    statusHistory.addLast(currentStatus().update(TaskState.FAILED))
  }

  public Status getStatus() {
    currentStatus()
  }

  public String toString() {
    getStatus().toString()
  }

  public void addResultObjects(List<Object>results){
    if (results) {
      currentStatus().ensureUpdateable()
      resultObjects.addAll(results)
    }
  }

  @Override
  List<Object> getResultObjects() {
    resultObjects.collect()
  }

  private DefaultTaskStatus currentStatus() {
    statusHistory.getLast() as DefaultTaskStatus
  }
}

@Immutable(knownImmutableClasses = [Status])
@CompileStatic
class TaskDisplayStatus implements Status {
  @JsonIgnore
  Status taskStatus

  static TaskDisplayStatus create(Status taskStatus) {
    new TaskDisplayStatus(taskStatus)
  }

  @Override
  String getStatus() {
    taskStatus.status
  }

  @Override
  String getPhase() {
    taskStatus.phase
  }

  @JsonIgnore
  Boolean isCompleted() { taskStatus.isCompleted() }

  @JsonIgnore
  Boolean isFailed() { taskStatus.isFailed() }
}

@Immutable
@CompileStatic
class DefaultTaskStatus implements Status {
  String phase
  String status

  @JsonIgnore
  TaskState state

  // Needed so that Java can interact with Groovy @Immutable classes.
  static DefaultTaskStatus create(String phase, String status, TaskState state) {
    new DefaultTaskStatus(phase, status, state)
  }

  @JsonProperty
  public Boolean isComplete() { state.isCompleted() }

  @JsonProperty
  public Boolean isCompleted() { state.isCompleted() }

  @JsonProperty
  public Boolean isFailed() { state.isFailed() }

  DefaultTaskStatus update(String phase, String status) {
    ensureUpdateable()
    new DefaultTaskStatus(phase, status, state)
  }

  DefaultTaskStatus update(TaskState state) {
    ensureUpdateable()
    new DefaultTaskStatus(phase, status, state)
  }

  public void ensureUpdateable() {
    if (isCompleted()) {
      throw new IllegalStateException("Task is already completed! No further updates allowed!")
    }
  }

}
