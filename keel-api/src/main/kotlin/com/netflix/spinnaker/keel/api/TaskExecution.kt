package com.netflix.spinnaker.keel.api

import java.time.Instant

interface TaskExecution {
  val id: String
  val name: String
  val application: String
  val startTime: Instant?
  val endTime: Instant?
  val status: TaskStatus
}