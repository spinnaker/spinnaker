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
import com.netflix.spinnaker.keel.api.actuation.SubjectType
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.events.TaskCreatedEvent
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.model.toOrcaNotification
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.TaskRecord
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Wraps [OrcaService] to make it easier to launch tasks in a standard way.
 */
@Component
class OrcaTaskLauncher(
  private val orcaService: OrcaService,
  private val repository: KeelRepository,
  private val publisher: ApplicationEventPublisher
) : TaskLauncher {
  override suspend fun submitJob(
    resource: Resource<*>,
    description: String,
    correlationId: String,
    stages: List<Map<String, Any?>>
  ) =
    submitJob(
      user = resource.serviceAccount,
      application = resource.application,
      notifications = resource.notifications,
      subject = resource.id,
      description = description,
      correlationId = correlationId,
      stages = stages,
      type = SubjectType.RESOURCE
    )

  override suspend fun submitJob(
    user: String,
    application: String,
    notifications: Set<NotificationConfig>,
    subject: String,
    description: String,
    correlationId: String,
    stages: List<Map<String, Any?>>,
    type: SubjectType,
    artifacts: List<Map<String, Any?>>
  ) =
    orcaService
      .orchestrate(
        user,
        OrchestrationRequest(
          name = description,
          application = application,
          description = description,
          job = stages.map { Job(it["type"].toString(), it) },
          trigger = OrchestrationTrigger(
            correlationId = correlationId,
            notifications = notifications.map { it.toOrcaNotification() },
            artifacts = artifacts
          )
        )
      )
      .let {
        log.info("Started task {} to upsert {}", it.ref, subject)
        publisher.publishEvent(TaskCreatedEvent(
          TaskRecord(id = it.taskId, name = description, subject = "$type:$subject")))
        Task(id = it.taskId, name = description)
      }

  override suspend fun correlatedTasksRunning(correlationId: String): Boolean =
    orcaService
      .getCorrelatedExecutions(correlationId)
      .isNotEmpty()

  private val Resource<*>.notifications: Set<NotificationConfig>
    get() = repository
      .environmentFor(id)
      .notifications
      .toSet()

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
