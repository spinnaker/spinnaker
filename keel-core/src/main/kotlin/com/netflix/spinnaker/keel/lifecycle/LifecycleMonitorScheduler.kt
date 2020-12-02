package com.netflix.spinnaker.keel.lifecycle

import com.netflix.spinnaker.config.LifecycleConfig
import com.netflix.spinnaker.keel.activation.ApplicationDown
import com.netflix.spinnaker.keel.activation.ApplicationUp
import com.netflix.spinnaker.keel.telemetry.LifecycleMonitorLoadFailed
import com.netflix.spinnaker.keel.telemetry.LifecycleMonitorTimedOut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

@Component
@EnableConfigurationProperties(LifecycleConfig::class)
class LifecycleMonitorScheduler(
  val monitors: List<LifecycleMonitor>,
  val monitorRepository: LifecycleMonitorRepository,
  val publisher: ApplicationEventPublisher,
  val lifecycleConfig: LifecycleConfig
) : CoroutineScope {
  override val coroutineContext: CoroutineContext = Dispatchers.IO

  private val enabled = AtomicBoolean(false)

  @EventListener(ApplicationUp::class)
  fun onApplicationUp() {
    log.info("Application up, enabling scheduled lifecycle monitoring")
    enabled.set(true)
  }

  @EventListener(ApplicationDown::class)
  fun onApplicationDown() {
    log.info("Application down, disabling scheduled lifecycle monitoring")
    enabled.set(false)
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * Listens for a not started event that a subclass can handle, and saves that into
   * the database for monitoring.
   *
   * //todo eb: add a 'monitor' flag to lifecycle events so we don't listen for a NOT_STARTED event here,
   *   it's kind of confusing.
   */
  @EventListener(LifecycleEvent::class)
  fun onLifecycleEvent(event: LifecycleEvent) {
    if (event.status == LifecycleEventStatus.NOT_STARTED
      && monitors.any { it.handles(event.type) }
      && event.link != null) {
      log.debug("${this.javaClass.simpleName} saving monitor event $event")
      monitorRepository.save(MonitoredTask(event, event.link))
    }
  }

  @Scheduled(fixedDelayString = "\${keel.lifecycle-monitor.frequency:PT1S}")
  fun invokeMonitoring() {
    if (enabled.get()) {

      val job: Job = launch {
        supervisorScope {
          runCatching {
            monitorRepository
              .tasksDueForCheck(lifecycleConfig.minAgeDuration, lifecycleConfig.batchSize)
          }
            .onFailure {
              publisher.publishEvent(LifecycleMonitorLoadFailed(it))
            }
            .onSuccess { tasks ->
              tasks.forEach { task ->
                try {
                  /**
                   * Allow individual monitoring to timeout but catch the `CancellationException`
                   * to prevent the cancellation of all coroutines under [job]
                   */
                  /**
                   * Allow individual monitoring to timeout but catch the `CancellationException`
                   * to prevent the cancellation of all coroutines under [job]
                   */
                  withTimeout(lifecycleConfig.timeoutDuration.toMillis()) {
                    launch {
                      monitors
                        .first { it.handles(task.type) }
                        .monitor(task)
                    }
                  }
                } catch (e: TimeoutCancellationException) {
                  log.error("Timed out monitoring task $task", e)
                  publisher.publishEvent(LifecycleMonitorTimedOut(task.type, task.link, task.triggeringEvent.artifactRef))
                }
              }
            }
        }
      }
      runBlocking { job.join() }
    }
  }
}
