/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.sql

import com.netflix.spinnaker.clouddriver.data.task.Status
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskDisplayStatus
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import org.slf4j.LoggerFactory

class SqlTask(
  private val id: String,
  internal val ownerId: String,
  internal val requestId: String,
  internal val startTimeMs: Long,
  private val repository: SqlTaskRepository
) : Task {

  companion object {
    private val log = LoggerFactory.getLogger(SqlTask::class.java)
  }

  override fun getId() = id
  override fun getOwnerId() = ownerId
  override fun getStartTimeMs() = startTimeMs

  override fun getResultObjects() = repository.getResultObjects(this)

  override fun addResultObjects(results: MutableList<Any>) {
    if (results.isEmpty()) {
      return
    }
    repository.addResultObjects(results, this)
  }

  override fun getHistory(): List<Status> {
    val status = repository.getHistory(this).map { TaskDisplayStatus(it) }
    return if (status.isNotEmpty() && status.last().isCompleted) {
      status.subList(0, status.size - 1)
    } else {
      status
    }
  }

  override fun updateStatus(phase: String, status: String) {
    repository.updateCurrentStatus(this, phase, status)
    log.debug("Updated status: phase={} status={}", phase, status)
  }

  override fun complete() {
    repository.updateState(this, TaskState.COMPLETED)
  }

  override fun fail() {
    repository.updateState(this, TaskState.FAILED)
  }

  override fun getStatus(): Status? {
    return repository.currentState(this)
  }
}
