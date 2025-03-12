package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.keel.persistence.TaskRecord

data class TaskCreatedEvent(
  val taskRecord: TaskRecord
)
