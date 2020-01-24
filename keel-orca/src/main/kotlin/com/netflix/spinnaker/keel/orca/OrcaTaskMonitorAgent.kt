package com.netflix.spinnaker.keel.orca

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.SubjectType
import com.netflix.spinnaker.keel.events.ResourceTaskFailed
import com.netflix.spinnaker.keel.events.ResourceTaskSucceeded
import com.netflix.spinnaker.keel.events.TaskCreatedEvent
import com.netflix.spinnaker.keel.persistence.NoSuchResourceId
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.TaskTrackingRepository
import com.netflix.spinnaker.keel.scheduled.ScheduledAgent
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class OrcaTaskMonitorAgent(
  private val taskTrackingRepository: TaskTrackingRepository,
  private val resourceRepository: ResourceRepository,
  private val orcaService: OrcaService,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) : ScheduledAgent {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override val lockTimeoutSeconds = TimeUnit.MINUTES.toSeconds(1)

  private val mapper = configuredObjectMapper()

  @EventListener(TaskCreatedEvent::class)
  fun onTaskEvent(event: TaskCreatedEvent) {
    taskTrackingRepository.store(event.taskRecord)
  }

  // 1. Get active tasks from task tracking table
  // 2. For each task, call orca and ask for details
  // 3. For each completed task, will emit an event for success/failure
  override suspend fun invokeAgent() {
    coroutineScope {
      taskTrackingRepository.getTasks()
        .associate {
          it.subject to
            async {
              orcaService.getOrchestrationExecution(it.id)
            }
        }
        .mapValues { it.value.await() }
        .filterValues { it.status.isComplete() }
        .map { (resourceId, taskDetails) ->

          // only resource events are supported currently
          if (resourceId.startsWith(SubjectType.RESOURCE.toString())) {
            val id = resourceId.substringAfter(":")
            try {
              when (taskDetails.status.isSuccess()) {
                true -> publisher.publishEvent(
                  ResourceTaskSucceeded(
                    resourceRepository.get(ResourceId(id)), clock))
                false -> publisher.publishEvent(
                  ResourceTaskFailed(
                    resourceRepository.get(ResourceId(id)), taskDetails.execution.stages.getFailureMessage(), clock))
              }
            } catch (e: NoSuchResourceId) {
              log.warn("No resource found for id $resourceId")
            }
          }
          taskTrackingRepository.delete(taskDetails.id)
        }
    }
  }

    // make sure it's only 1 context per run
    private fun List<Map<String, Any>>?.getFailureMessage(): String? {
      if (this.isNullOrEmpty()) {
        return ""
      }
      // since this is a single task, we expecting to get only 1 error message
      val context: OrcaContext? = this.first()["context"]?.let { mapper.convertValue(it) }

      return context?.exception?.details?.errors?.joinToString(",")
    }
  }
