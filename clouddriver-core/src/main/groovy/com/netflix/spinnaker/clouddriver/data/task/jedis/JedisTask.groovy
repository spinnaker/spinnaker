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

package com.netflix.spinnaker.clouddriver.data.task.jedis

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.netflix.spinnaker.clouddriver.data.task.SagaId
import com.netflix.spinnaker.clouddriver.data.task.Status
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import groovy.util.logging.Slf4j

import javax.annotation.Nonnull

// The fields of the task are computed on-demand by querying the repository. This means that the
// serialized task may not be internally consistent; each field will reflect the state of the task
// in the repository at the time that field's accessor was called during serialization.
// This is in general a difficult problem to solve with redis, which does not support atomic reads
// of multiple keys, but has been solved in the SQL repository by fetching all data in a single
// query. As a workaround, we'll instruct Jackson to serialize the status first. The reason is that
// consumers tend to use the status field to check if a task is complete, and expect the other
// fields to be filled out if it is. If there is an inconsistency between the status and other
// fields, we'd rather return a stale value in the status field than in other fields.
// In general, returning an older status (ie, still running) and newer other fields will just cause
// clients to poll again until they see the updated status. Returning a newer status (ie, completed
// or failed) but stale values in other fields will in general cause clients to use these stale
// values, leading to bugs.
// We'll force the history to be computed next (as clients could feasibly use this to determine
// whether a task is complete), then will not enforce an order on any other properties.
@JsonPropertyOrder(["status", "history"])
@Slf4j
class JedisTask implements Task {

  @JsonIgnore
  RedisTaskRepository repository

  final String id
  final long startTimeMs
  final String ownerId
  final String requestId
  final Set<SagaId> sagaIds

  @JsonIgnore
  final boolean previousRedis

  JedisTask(
    String id,
    long startTimeMs,
    RedisTaskRepository repository,
    String ownerId,
    List<SagaId> sagaIds,
    boolean previousRedis
  ) {
    this.id = id
    this.startTimeMs = startTimeMs
    this.repository = repository
    this.ownerId = ownerId
    this.sagaIds = sagaIds
    this.previousRedis = previousRedis
  }

  @Override
  void updateStatus(String phase, String status) {
    checkMutable()
    repository.addToHistory(repository.currentState(this).update(phase, status), this)
    log.info("[$phase] $status")
  }

  @Override
  void complete() {
    checkMutable()
    repository.addToHistory(repository.currentState(this).update(TaskState.COMPLETED), this)
  }

  @Deprecated
  @Override
  void fail() {
    checkMutable()
    repository.addToHistory(repository.currentState(this).update(TaskState.FAILED), this)
  }

  @Override
  void fail(boolean retryable) {
    checkMutable()
    repository.addToHistory(
      repository.currentState(this).update(retryable ? TaskState.FAILED_RETRYABLE : TaskState.FAILED),
      this
    )
  }

  @Override
  public void addResultObjects(List<Object> results) {
    checkMutable()
    if (results) {
      repository.currentState(this).ensureUpdateable()
      repository.addResultObjects(results, this)
    }
  }

  public List<Object> getResultObjects() {
    repository.getResultObjects(this)
  }

  public List<? extends Status> getHistory() {
    def status = repository.getHistory(this)
    if (status && status.last().isCompleted()) {
      status.subList(0, status.size()  - 1)
    } else {
      status
    }
  }

  @Override
  String getOwnerId() {
    return ownerId
  }

  @Override
  Status getStatus() {
    repository.currentState(this)
  }

  @Override
  void addSagaId(@Nonnull SagaId sagaId) {
    this.sagaIds.add(sagaId)
  }

  @Override
  boolean hasSagaIds() {
    return !sagaIds.isEmpty()
  }

  @Override
  void retry() {
    checkMutable()
    repository.addToHistory(
      repository.currentState(this).update(TaskState.STARTED),
      this
    )

  }

  private void checkMutable() {
    if (previousRedis) {
      throw new IllegalStateException("Read-only task")
    }
  }
}
