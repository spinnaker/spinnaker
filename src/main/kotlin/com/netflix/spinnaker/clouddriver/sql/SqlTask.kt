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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TOOD(rz): Refactor 'river to not use an active record pattern. This sucks.
 */
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

  private var resultObjects: MutableList<Any> = mutableListOf()
  private var history: MutableList<Status> = mutableListOf()

  private val resultObjectsDirty = AtomicBoolean(false)
  private val historyDirty = AtomicBoolean(false)

  override fun getId() = id
  override fun getOwnerId() = ownerId
  override fun getStartTimeMs() = startTimeMs
  override fun getStatus(): Status? = repository.getLatestState(this)

  override fun getResultObjects(): MutableList<Any> {
    if (resultObjectsDirty.getAndSet(false)) {
      resultObjects.clear()
      resultObjects.addAll(repository.getResultObjects(this))
    }
    return resultObjects
  }

  override fun addResultObjects(results: MutableList<Any>) {
    if (results.isEmpty()) {
      return
    }
    resultObjectsDirty.set(true)
    repository.addResultObjects(results, this)
  }

  override fun getHistory(): List<Status> {
    refreshHistoryState()

    val status = history.map { TaskDisplayStatus(it) }
    return if (status.isNotEmpty() && status.last().isCompleted) {
      status.subList(0, status.size - 1)
    } else {
      status
    }
  }

  override fun updateStatus(phase: String, status: String) {
    historyDirty.set(true)
    repository.updateCurrentStatus(this, phase, status)
    log.debug("Updated status: phase={} status={}", phase, status)
  }

  override fun complete() {
    historyDirty.set(true)
    repository.updateState(this, TaskState.COMPLETED)
  }

  override fun fail() {
    historyDirty.set(true)
    repository.updateState(this, TaskState.FAILED)
  }

  internal fun hydrateResultObjects(resultObjects: MutableList<Any>) {
    this.resultObjectsDirty.set(false)
    this.resultObjects = resultObjects
  }

  internal fun hydrateHistory(history: MutableList<Status>) {
    this.historyDirty.set(false)
    this.history = history
  }

  internal fun refreshHistoryState(force: Boolean = false) {
    if (historyDirty.getAndSet(false) || force) {
      repository.refreshTaskHistoryState(this).also {
        history.clear()
        history.addAll(it)
      }
    }
  }
}
