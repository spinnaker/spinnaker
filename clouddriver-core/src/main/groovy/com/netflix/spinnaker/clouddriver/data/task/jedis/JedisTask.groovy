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
import com.netflix.spinnaker.clouddriver.data.task.Status
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import groovy.util.logging.Slf4j

@Slf4j
class JedisTask implements Task {

  @JsonIgnore
  JedisTaskRepository repository

  final String id
  final long startTimeMs

  JedisTask(String id, long startTimeMs, JedisTaskRepository repository) {
    this.id = id
    this.startTimeMs = startTimeMs
    this.repository = repository
  }

  @Override
  void updateStatus(String phase, String status) {
    repository.addToHistory(repository.currentState(this).update(phase, status), this)
    log.info "[$phase] - $status"
  }

  @Override
  void complete() {
    repository.addToHistory(repository.currentState(this).update(TaskState.COMPLETED), this)
  }

  @Override
  void fail() {
    repository.addToHistory(repository.currentState(this).update(TaskState.FAILED), this)
  }

  @Override
  public void addResultObjects(List<Object> results) {
    if (results) {
      repository.currentState(this).ensureUpdateable()
      repository.addResultObjects(results, this)
    }
  }

  public List<Object> getResultObjects() {
    repository.getResultObjects(this)
  }

  public List<Status> getHistory() {
    def status = repository.getHistory(this)
    if (status && status.last().isCompleted()) {
      status.subList(0, status.size()  - 1)
    } else {
      status
    }
  }

  @Override
  Status getStatus() {
    repository.currentState(this)
  }
}
