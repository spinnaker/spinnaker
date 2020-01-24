package com.netflix.spinnaker.keel.persistence

interface TaskTrackingRepository {

  fun store(task: TaskRecord)
  fun getTasks(): Set<TaskRecord>
  fun delete(taskId: String)
}

data class TaskRecord(
  val id: String,
  val name: String,
  val subject: String
)
