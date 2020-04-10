package com.netflix.spinnaker.keel.orca

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.actuation.SubjectType.RESOURCE
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
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
              orcaService.getOrchestrationExecution(it.id, DEFAULT_SERVICE_ACCOUNT)
            }
        }
        .mapValues { it.value.await() }
        .filterValues { it.status.isComplete() }
        .map { (resourceId, taskDetails) ->
          // only resource events are currently supported
          if (resourceId.startsWith(RESOURCE.toString())) {
            val id = resourceId.substringAfter(":")
            try {
              when (taskDetails.status.isSuccess()) {
                true -> publisher.publishEvent(
                  ResourceTaskSucceeded(
                    resourceRepository.get(id), listOf(Task(taskDetails.id, taskDetails.name)), clock))
                false -> publisher.publishEvent(
                  ResourceTaskFailed(
                    resourceRepository.get(id), taskDetails.execution.stages.getFailureMessage() ?: "", listOf(Task(taskDetails.id, taskDetails.name)), clock))
              }
            } catch (e: NoSuchResourceId) {
              log.warn("No resource found for id $resourceId")
            }
          }
          taskTrackingRepository.delete(taskDetails.id)
        }
    }
  }

  // get the exception - can be either general orca exception or kato specific
  private fun List<Map<String, Any>>?.getFailureMessage(): String? {

    this?.forEach { it ->
      val context: OrcaContext? = it["context"]?.let { mapper.convertValue(it) }

      // find the first exception and return
      if (context?.exception != null) {
        return context.exception.details.errors.joinToString(",")
      }

      if (context?.clouddriverException != null) {
        val clouddriverError: ClouddriverException? = context.clouddriverException.first()["exception"]?.let { mapper.convertValue(it) }
        return clouddriverError?.message
      }
    }

    return null
  }
}
