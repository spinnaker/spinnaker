package com.netflix.spinnaker.keel.lifecycle

import com.netflix.spinnaker.kork.exceptions.SystemException

class LinkNotFound(task: MonitoredTask) : SystemException(
  "Task contains no link to monitor: $task"
)
