package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.api.actuation.SubjectType
import java.time.Instant

interface TaskTrackingRepository {
  fun store(task: TaskRecord)
  fun getIncompleteTasks(): Set<TaskRecord>
  fun updateStatus(taskId: String, status: TaskStatus)
  fun getTasks(resourceId: String, limit: Int = 5): Set<TaskForResource>
  fun getInFlightTasks(application: String, environmentName: String): Set<TaskForResource>
  fun delete(taskId: String)

  /**
   * @return all running tasks, plus an completed tasks that were
   *   launched in that "batch" (within 30 seconds of them)
   */
  fun getLatestBatchOfTasks(resourceId: String): Set<TaskForResource>
}

data class TaskRecord(
  val id: String,
  val name: String,
  val subjectType: SubjectType,
  val application: String,
  val environmentName: String?,
  val resourceId: String?,
  val artifactVersion: String?
)

data class TaskForResource(
  val id: String,
  val name: String,
  val resourceId: String,
  val startedAt: Instant,
  val endedAt: Instant?,
  val artifactVersion: String?
)
