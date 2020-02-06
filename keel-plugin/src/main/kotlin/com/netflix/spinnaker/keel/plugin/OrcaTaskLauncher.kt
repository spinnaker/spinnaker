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
package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SubjectType
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.events.TaskCreatedEvent
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrcaNotification
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.model.toOrcaNotification
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.TaskRecord
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Wraps [OrcaService] to make it easier to launch tasks in a standard way.
 */
@Component
class OrcaTaskLauncher(
  private val orcaService: OrcaService,
  private val deliveryConfigRepository: DeliveryConfigRepository,
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
    notifications: List<OrcaNotification>,
    subject: String,
    description: String,
    correlationId: String,
    stages: List<Map<String, Any?>>,
    type: SubjectType,
    artifacts: List<Artifact>
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
            notifications = notifications,
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

  private val Resource<*>.notifications: List<OrcaNotification>
    get() = deliveryConfigRepository
      .environmentFor(id)
      .notifications
      .map { it.toOrcaNotification() }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
