package com.netflix.spinnaker.keel.lifecycle

import com.netflix.spinnaker.config.LifecycleConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher

/**
 * Abstract lifecycle monitor that coordinates monitoring tasks while the
 * application is up.
 *
 * Implementations are responsible for doing the monitoring, emitting [LifecycleEvent]s
 * as the task transitions through different stages, updating the text / link to user
 * friendly things (w/in the [LifecycleEvent]s emitted), and calling [endMonitoringOfTask]
 * when the monitoring is finished.
 */
@EnableConfigurationProperties(LifecycleConfig::class)
abstract class LifecycleMonitor(
  open val monitorRepository: LifecycleMonitorRepository,
  open val publisher: ApplicationEventPublisher,
  open val lifecycleConfig: LifecycleConfig
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  init {
    log.debug("Lifecycle monitor ${javaClass.simpleName} enabled")
  }

  /**
   * @return true if this monitor can handle the event type
   */
  abstract fun handles(type: LifecycleEventType): Boolean

  /**
   * Checks the status of the task, and emits Lifecycle event
   * when the task is running, completed, or unknown.
   *
   * Removes the event from the repository when finished monitoring.
   */
  abstract suspend fun monitor(task: MonitoredTask)

  /**
   * Should be called from inside the [monitor] function when we are done
   * monitoring the task.
   */
  fun endMonitoringOfTask(task: MonitoredTask) {
    log.debug("${this.javaClass.simpleName} has completed monitoring for $task")
    monitorRepository.delete(task)
  }

  /**
   * Handles persisting failures and publishing exception events.
   *
   * Call when there was a failure getting status.
   */
  fun handleFailureFetchingStatus(task: MonitoredTask) {
    if (task.numFailures >= lifecycleConfig.numFailuresAllowed - 1) {
      log.warn("Too many consecutive errors (${lifecycleConfig.numFailuresAllowed}) " +
        "fetching the task status for $task. Giving up.")
      endMonitoringOfTask(task)
      publishExceptionEvent(task)
    } else {
      monitorRepository.markFailureGettingStatus(task)
    }
  }

  /**
   * Handles clearing the failure status if there was one.
   *
   * Call when status has successfully been fetched.
   */
  fun markSuccessFetchingStatus(task: MonitoredTask) {
    if (task.numFailures > 0) {
      // we only care about consecutive failures, and we just had a success
      monitorRepository.clearFailuresGettingStatus(task)
    }
  }

  /**
   * Called when we end monitoring of a task because we've had too
   * many exceptions or failures getting the status.
   *
   * Should emit a lifecycle event with status UNKNOWN
   */
  abstract fun publishExceptionEvent(task: MonitoredTask)
}
