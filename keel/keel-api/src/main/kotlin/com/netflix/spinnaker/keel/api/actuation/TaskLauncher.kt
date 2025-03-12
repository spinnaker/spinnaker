package com.netflix.spinnaker.keel.api.actuation

import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.TaskExecution
import java.util.concurrent.CompletableFuture

interface TaskLauncher {
  suspend fun submitJob(
    resource: Resource<*>,
    description: String,
    correlationId: String,
    job: Job
  ): Task =
    submitJob(
      resource = resource,
      description = description,
      correlationId = correlationId,
      stages = listOf(job),
      artifactVersion = null
    )

  suspend fun submitJob(
    resource: Resource<*>,
    description: String,
    correlationId: String,
    stages: List<Job>,
    artifactVersion: String? = null
  ): Task

  fun submitJobAsync(
    resource: Resource<*>,
    description: String,
    correlationId: String,
    stages: List<Map<String, Any?>>,
    artifactVersion: String? = null
  ): CompletableFuture<Task>

  suspend fun submitJob(
    user: String,
    application: String,
    notifications: Set<NotificationConfig>,
    environmentName: String?,
    resourceId: String?,
    description: String,
    correlationId: String? = null,
    stages: List<Job>,
    artifacts: List<Map<String, Any?>> = emptyList(),
    parameters: Map<String, Any> = emptyMap(),
    artifactVersion: String? = null
  ): Task =
    submitJob(
      user = user,
      application = application,
      notifications = notifications,
      environmentName = environmentName,
      resourceId = resourceId,
      description = description,
      correlationId = correlationId,
      stages = stages,
      type = SubjectType.CONSTRAINT,
      artifacts = artifacts,
      parameters = parameters,
      artifactVersion = artifactVersion
    )

  /**
   * Submits the list of actuation jobs specified by [stages].
   *
   * Implementations should call any registered [JobInterceptor] plugins on the list before
   * submitting the jobs for execution.
   */
  suspend fun submitJob(
    user: String,
    application: String,
    notifications: Set<NotificationConfig>,
    environmentName: String?,
    resourceId: String?,
    description: String,
    correlationId: String? = null,
    stages: List<Job>,
    type: SubjectType,
    artifacts: List<Map<String, Any?>> = emptyList(),
    parameters: Map<String, Any> = emptyMap(),
    artifactVersion: String? = null
  ): Task

  suspend fun correlatedTasksRunning(correlationId: String): Boolean

  /**
   * @return The [TaskExecution] matching the [taskId].
   */
  suspend fun getTaskExecution(taskId: String): TaskExecution

  /**
   * Cancels the given tasks as the provided user identity
   */
  suspend fun cancelTasks(taskIds: List<String>, user: String)
}
