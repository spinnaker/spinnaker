package com.netflix.spinnaker.keel.orca

import com.netflix.spinnaker.keel.api.actuation.SubjectType.RESOURCE
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.events.ResourceTaskFailed
import com.netflix.spinnaker.keel.events.ResourceTaskSucceeded
import com.netflix.spinnaker.keel.events.TaskCreatedEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleMonitor
import com.netflix.spinnaker.keel.persistence.NoSuchResourceId
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.TaskTrackingRepository
import com.netflix.spinnaker.keel.retrofit.isNotFound
import com.netflix.spinnaker.keel.scheduled.ScheduledAgent
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import retrofit2.HttpException
import java.time.Clock
import java.util.concurrent.TimeUnit.MINUTES

/**
 * This class monitors all tasks in flight.
 *
 * todo eb: Convert to [LifecycleMonitor] to take advantage of batch sizing and the same
 *  way of distributing checking across instances.
 */
@Component
class OrcaTaskMonitorAgent(
  private val taskTrackingRepository: TaskTrackingRepository,
  private val resourceRepository: ResourceRepository,
  private val orcaService: OrcaService,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) : ScheduledAgent {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override val lockTimeoutSeconds = MINUTES.toSeconds(1)

  private val mapper = configuredObjectMapper()

  @EventListener(TaskCreatedEvent::class)
  fun onTaskEvent(event: TaskCreatedEvent) {
    taskTrackingRepository.store(event.taskRecord)
  }

  // 1. Get active tasks from task tracking table
  // 2. For each task, call orca and ask for details
  // 3. For each completed task, will emit an event for success/failure and delete from the table
  override suspend fun invokeAgent() {
    coroutineScope {
      taskTrackingRepository.getIncompleteTasks().associateWith {
        async {
          try {
            orcaService.getOrchestrationExecution(it.id, DEFAULT_SERVICE_ACCOUNT)
          } catch (e: HttpException) {
            when (e.isNotFound) {
              true -> {
                log.warn(
                  "Exception ${e.message} has caught while calling orca to fetch status for execution id: ${it.id}" +
                    " Possible reason: orca is saving info for 2000 tasks/app and this task is older."
                )
                // when we get not found exception from orca, we shouldn't try to get the status anymore
                taskTrackingRepository.delete(it.id)
              }
              else -> log.warn(
                "Exception ${e.message} has caught while calling orca to fetch status for execution id: ${it.id}",
                e
              )
            }
            null
          }
        }.await()
      }
        .filterValues { it != null && it.status.isComplete() }
        .map { (task, taskDetails) ->
          if (taskDetails != null) {
            // only resource events are currently supported
            if (task.subjectType == RESOURCE) {
              val id = checkNotNull(task.resourceId)
              try {
                when (taskDetails.status.isSuccess()) {
                  true -> publisher.publishEvent(
                    ResourceTaskSucceeded(
                      resourceRepository.get(id), listOf(Task(taskDetails.id, taskDetails.name)), clock
                    )
                  )
                  false -> publisher.publishEvent(
                    ResourceTaskFailed(
                      resourceRepository.get(id),
                      taskDetails.execution?.stages.getFailureMessage(mapper)
                        ?: "",
                      listOf(Task(taskDetails.id, taskDetails.name)), clock
                    )
                  )
                }
              } catch (e: NoSuchResourceId) {
                log.warn("No resource found for id $task")
              }
            }
            taskTrackingRepository.updateStatus(task.id, taskDetails.status)
          }
        }
    }
  }
}
