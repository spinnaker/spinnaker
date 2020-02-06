package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SubjectType
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.model.OrcaNotification
import com.netflix.spinnaker.kork.artifacts.model.Artifact

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
    notifications: List<OrcaNotification>,
    subject: String,
    description: String,
    correlationId: String,
    stages: List<Map<String, Any?>>,
    artifacts: List<Artifact> = emptyList()
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
      artifacts = artifacts
    )

  suspend fun submitJob(
    user: String,
    application: String,
    notifications: List<OrcaNotification>,
    subject: String,
    description: String,
    correlationId: String,
    stages: List<Map<String, Any?>>,
    type: SubjectType,
    artifacts: List<Artifact> = emptyList()
  ): Task
}
