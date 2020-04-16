package com.netflix.spinnaker.keel.api.actuation

import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.Resource

interface TaskLauncher {
  suspend fun submitJob(
    resource: Resource<*>,
    description: String,
    correlationId: String,
    job: Map<String, Any?>
  ): Task =
    submitJob(
      resource = resource,
      description = description,
      correlationId = correlationId,
      stages = listOf(job)
    )

  suspend fun submitJob(
    resource: Resource<*>,
    description: String,
    correlationId: String,
    stages: List<Map<String, Any?>>
  ): Task

  suspend fun submitJob(
    user: String,
    application: String,
    notifications: Set<NotificationConfig>,
    subject: String,
    description: String,
    correlationId: String,
    stages: List<Map<String, Any?>>,
    artifacts: List<Map<String, Any?>> = emptyList(),
    parameters: Map<String, Any> = emptyMap()
  ): Task =
    submitJob(
      user = user,
      application = application,
      notifications = notifications,
      subject = subject,
      description = description,
      correlationId = correlationId,
      stages = stages,
      type = SubjectType.CONSTRAINT,
      artifacts = artifacts,
      parameters = parameters
    )

  suspend fun submitJob(
    user: String,
    application: String,
    notifications: Set<NotificationConfig>,
    subject: String,
    description: String,
    correlationId: String,
    stages: List<Map<String, Any?>>,
    type: SubjectType,
    artifacts: List<Map<String, Any?>> = emptyList(),
    parameters: Map<String, Any> = emptyMap()
  ): Task

  suspend fun correlatedTasksRunning(correlationId: String): Boolean
}
