/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.keel.orca

import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.TaskExecution
import com.netflix.spinnaker.keel.api.actuation.Job
import com.netflix.spinnaker.keel.api.actuation.SubjectType
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.JobInterceptor
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.events.TaskCreatedEvent
import com.netflix.spinnaker.keel.model.OrcaNotification
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.model.toOrcaNotification
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.TaskRecord
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * Wraps [OrcaService] to make it easier to launch tasks in a standard way.
 */
@Component
class OrcaTaskLauncher(
  private val orcaService: OrcaService,
  private val repository: KeelRepository,
  private val publisher: EventPublisher,
  private val springEnv: Environment,
  private val jobInterceptors: List<JobInterceptor> = emptyList()
) : TaskLauncher {

  private val isNewSlackEnabled: Boolean
    get() = springEnv.getProperty("keel.notifications.slack", Boolean::class.java, true)


  override suspend fun submitJob(
    resource: Resource<*>,
    description: String,
    correlationId: String,
    stages: List<Job>,
    artifactVersion: String?
  ) =
    submitJob(
      user = resource.serviceAccount,
      application = resource.application,
      notifications = resource.notifications,
      environmentName = repository.environmentFor(resource.id).name,
      resourceId = resource.id,
      description = description,
      correlationId = correlationId,
      stages = stages,
      type = SubjectType.RESOURCE,
      artifactVersion = artifactVersion
    )

  override fun submitJobAsync(
    resource: Resource<*>,
    description: String,
    correlationId: String,
    stages: List<Job>,
    artifactVersion: String?
  ): CompletableFuture<Task> = GlobalScope.future {
    submitJob(resource, description, correlationId, stages, artifactVersion)
  }

  override suspend fun submitJob(
    user: String,
    application: String,
    notifications: Set<NotificationConfig>,
    environmentName: String?,
    resourceId: String?,
    description: String,
    correlationId: String?,
    stages: List<Job>,
    type: SubjectType,
    artifacts: List<Map<String, Any?>>,
    parameters: Map<String, Any>,
    artifactVersion: String?
  ) =
    orcaService
      .orchestrate(
        user,
        OrchestrationRequest(
          name = description,
          application = application,
          description = description,
          job = jobInterceptors.fold(stages) { updatedStages, interceptor ->
            interceptor.intercept(updatedStages, user)
          },
          trigger = OrchestrationTrigger(
            correlationId = correlationId,
            notifications = generateNotifications(notifications),
            artifacts = artifacts,
            parameters = parameters
          )
        )
      )
      .let {
        log.info("Started task {} to upsert {}", it.ref, resourceId)
        publisher.publishEvent(
          TaskCreatedEvent(
            TaskRecord(
              id = it.taskId,
              name = description,
              subjectType = type,
              application = application,
              environmentName = environmentName,
              resourceId = resourceId,
              artifactVersion = artifactVersion
            )
          )
        )
        Task(id = it.taskId, name = description)
      }

  //TODO[gyardeni]: remove this function and just return an empty list for orca notifications,
  //as all notifications will be controlled from keel directly from now on.
  private fun generateNotifications(notifications: Set<NotificationConfig>): List<OrcaNotification> {
    return if (!isNewSlackEnabled)
      notifications.map { it.toOrcaNotification() }
    else {
      emptyList()
    }
  }

  override suspend fun correlatedTasksRunning(correlationId: String): Boolean =
    orcaService
      .getCorrelatedExecutions(correlationId, DEFAULT_SERVICE_ACCOUNT)
      .isNotEmpty()

  override suspend fun getTaskExecution(taskId: String): TaskExecution =
    orcaService.getOrchestrationExecution(taskId)

  override suspend fun cancelTasks(taskIds: List<String>, user: String) =
    orcaService.cancelOrchestrations(taskIds, user)

  private val Resource<*>.notifications: Set<NotificationConfig>
    get() = repository
      .environmentFor(id)
      .notifications
      .toSet()

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
