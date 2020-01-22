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
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.model.EchoNotification
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.model.toEchoNotification
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Wraps [OrcaService] to make it easier to launch tasks in a standard way.
 */
@Component
class TaskLauncher(
  private val orcaService: OrcaService,
  private val deliveryConfigRepository: DeliveryConfigRepository
) {
  suspend fun submitJobToOrca(
    resource: Resource<*>,
    description: String,
    correlationId: String,
    job: Map<String, Any?>
  ): Task = submitJobToOrca(
    resource = resource,
    description = description,
    correlationId = correlationId,
    stages = listOf(job)
  )

  suspend fun submitJobToOrca(
    resource: Resource<*>,
    description: String,
    correlationId: String,
    stages: List<Map<String, Any?>>
  ): Task =
    submitJobToOrca(
      user = resource.serviceAccount,
      application = resource.application,
      notifications = resource.notifications,
      subject = resource.id.value,
      description = description,
      correlationId = correlationId,
      stages = stages
    )

  suspend fun submitJobToOrca(
    user: String,
    application: String,
    notifications: List<EchoNotification>,
    subject: String,
    description: String,
    correlationId: String,
    stages: List<Map<String, Any?>>
  ): Task =
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
            notifications = notifications
          )
        )
      )
      .let {
        log.info("Started task {} to upsert {}", it.ref, subject)
        Task(id = it.taskId, name = description)
      }

  private val Resource<*>.notifications: List<EchoNotification>
    get() = deliveryConfigRepository
      .environmentFor(id)
      .notifications
      .map { it.toEchoNotification() }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
