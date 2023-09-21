package com.netflix.spinnaker.keel.actuation

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.ArtifactCheckConfig
import com.netflix.spinnaker.config.EnvironmentCheckConfig
import com.netflix.spinnaker.config.EnvironmentDeletionConfig
import com.netflix.spinnaker.config.EnvironmentVerificationConfig
import com.netflix.spinnaker.config.PostDeployActionsConfig
import com.netflix.spinnaker.config.ResourceCheckConfig
import com.netflix.spinnaker.keel.activation.ApplicationDown
import com.netflix.spinnaker.keel.activation.ApplicationUp
import com.netflix.spinnaker.keel.exceptions.EnvironmentCurrentlyBeingActedOn
import com.netflix.spinnaker.keel.logging.TracingSupport.Companion.blankMDC
import com.netflix.spinnaker.keel.persistence.AgentLockRepository
import com.netflix.spinnaker.keel.persistence.EnvironmentDeletionRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.postdeploy.PostDeployActionRunner
import com.netflix.spinnaker.keel.telemetry.AgentInvocationComplete
import com.netflix.spinnaker.keel.telemetry.ArtifactCheckComplete
import com.netflix.spinnaker.keel.telemetry.ArtifactCheckTimedOut
import com.netflix.spinnaker.keel.telemetry.EnvironmentsCheckTimedOut
import com.netflix.spinnaker.keel.telemetry.PostDeployActionCheckComplete
import com.netflix.spinnaker.keel.telemetry.PostDeployActionTimedOut
import com.netflix.spinnaker.keel.telemetry.ResourceCheckCompleted
import com.netflix.spinnaker.keel.telemetry.ResourceCheckTimedOut
import com.netflix.spinnaker.keel.telemetry.ResourceLoadFailed
import com.netflix.spinnaker.keel.telemetry.VerificationCheckComplete
import com.netflix.spinnaker.keel.telemetry.VerificationTimedOut
import com.netflix.spinnaker.keel.telemetry.recordDurationPercentile
import com.netflix.spinnaker.keel.verification.VerificationRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.math.max

@EnableConfigurationProperties(
  ResourceCheckConfig::class,
  EnvironmentDeletionConfig::class,
  EnvironmentVerificationConfig::class,
  PostDeployActionsConfig::class,
  EnvironmentCheckConfig::class,
  ArtifactCheckConfig::class
)
@Component
class CheckScheduler(
  private val repository: KeelRepository,
  private val environmentDeletionRepository: EnvironmentDeletionRepository,
  private val resourceActuator: ResourceActuator,
  private val environmentPromotionChecker: EnvironmentPromotionChecker,
  private val verificationRunner: VerificationRunner,
  private val artifactHandlers: Collection<ArtifactHandler>,
  private val postDeployActionRunner: PostDeployActionRunner,
  private val resourceCheckConfig: ResourceCheckConfig,
  private val environmentCheckConfig: EnvironmentCheckConfig,
  private val artifactCheckConfig: ArtifactCheckConfig,
  private val verificationConfig: EnvironmentVerificationConfig,
  private val postDeployConfig: PostDeployActionsConfig,
  private val environmentDeletionConfig: EnvironmentDeletionConfig,
  private val environmentCleaner: EnvironmentCleaner,
  private val publisher: ApplicationEventPublisher,
  private val agentLockRepository: AgentLockRepository,
  private val clock: Clock,
  private val springEnv: Environment,
  private val spectator: Registry
  ) : CoroutineScope {
  override val coroutineContext: CoroutineContext = Dispatchers.IO

  private val enabled = AtomicBoolean(false)

  @EventListener(ApplicationUp::class)
  fun onApplicationUp() {
    log.info("Application up, enabling scheduled resource checks")
    enabled.set(true)
  }

  @EventListener(ApplicationDown::class)
  fun onApplicationDown() {
    log.info("Application down, disabling scheduled resource checks")
    enabled.set(false)
  }

  // Used for resources, environments, and artifacts.
  private val checkMinAge: Duration
    get() = springEnv.getProperty("keel.check.min-age-duration", Duration::class.java, resourceCheckConfig.minAgeDuration)

  private val resourceBatchSize: Int
    get() = springEnv.getProperty("keel.resource-check.batch-size", Int::class.java, resourceCheckConfig.batchSize)

  private val environmentBatchSize: Int
    get() = springEnv.getProperty("keel.environment-check.batch-size", Int::class.java, environmentCheckConfig.batchSize)

  private val artifactBatchSize: Int
    get() = springEnv.getProperty("keel.artifact-check.batch-size", Int::class.java, artifactCheckConfig.batchSize)

  private val verificationBatchSize: Int
    get() = springEnv.getProperty("keel.verification.batch-size", Int::class.java, verificationConfig.batchSize)

  private val postDeployBatchSize: Int
    get() = springEnv.getProperty("keel.post-deploy.batch-size", Int::class.java, postDeployConfig.batchSize)

  @Scheduled(fixedDelayString = "\${keel.resource-check.frequency:PT1S}")
  fun checkResources() {
    if (enabled.get()) {
      val startTime = clock.instant()
      val job = launch(blankMDC) {
        supervisorScope {
          runCatching {
            repository
              .resourcesDueForCheck(checkMinAge, resourceBatchSize)
          }
            .onFailure {
              publisher.publishEvent(ResourceLoadFailed(it))
            }
            .onSuccess {
              it.forEach {
                try {
                  /**
                   * Allow individual resource checks to timeout but catch the `CancellationException`
                   * to prevent the cancellation of all coroutines under [job]
                   */
                  withTimeout(resourceCheckConfig.timeoutDuration.toMillis()) {
                    launch {
                      resourceActuator.checkResource(it)
                      publisher.publishEvent(ResourceCheckCompleted(Duration.between(startTime, clock.instant())))
                    }
                  }
                } catch (e: TimeoutCancellationException) {
                  log.error("Timed out checking resource ${it.id}", e)
                  publisher.publishEvent(ResourceCheckTimedOut(it.kind, it.id, it.application))
                }
              }
            }
        }
      }
      runBlocking { job.join() }
      recordDuration(startTime, "resource")
    }
  }

  @Scheduled(fixedDelayString = "\${keel.environment-check.frequency:PT1S}")
  fun checkEnvironments() {
    if (enabled.get()) {
      val startTime = clock.instant()
      publisher.publishEvent(ScheduledEnvironmentCheckStarting)

      val job = launch(blankMDC) {
        supervisorScope {
          repository
            .deliveryConfigsDueForCheck(checkMinAge, environmentBatchSize)
            .forEach {
              try {
                /**
                 * Sets the timeout to (checkTimeout * environmentCount), since a delivery-config's
                 * environments are checked sequentially within one coroutine job.
                 *
                 * TODO: consider refactoring environmentPromotionChecker so that it can be called for
                 *  individual environments, allowing fairer timeouts.
                 */
                withTimeout(environmentCheckConfig.timeoutDuration.toMillis() * max(it.environments.size, 1)) {
                  launch { environmentPromotionChecker.checkEnvironments(it) }
                }
              } catch (e: TimeoutCancellationException) {
                log.error("Timed out checking environments for ${it.application}/${it.name}", e)
                publisher.publishEvent(EnvironmentsCheckTimedOut(it.application, it.name))
              } finally {
                repository.markDeliveryConfigCheckComplete(it)
              }
            }
        }
      }

      runBlocking { job.join() }
      recordDuration(startTime, "environment")
    }
  }

  @Scheduled(fixedDelayString = "\${keel.environment-deletion.check.frequency:PT1S}")
  fun checkEnvironmentsForDeletion() {
    if (enabled.get()) {
      val startTime = clock.instant()

      val job = launch(blankMDC) {
        supervisorScope {
          environmentDeletionRepository
            .itemsDueForCheck(checkMinAge, environmentBatchSize)
            .forEach {
              try {
                withTimeout(environmentDeletionConfig.check.timeoutDuration.toMillis()) {
                  launch { environmentCleaner.cleanupEnvironment(it) }
                }
              } catch (e: TimeoutCancellationException) {
                log.error("Timed out checking environment ${it.name} for deletion", e)
              }
            }
        }
      }

      runBlocking { job.join() }
      recordDuration(startTime, "environmentDeletion")
    }
  }

  @Scheduled(fixedDelayString = "\${keel.artifact-check.frequency:PT1S}")
  fun checkArtifacts() {
    if (enabled.get()) {
      val startTime = clock.instant()
      publisher.publishEvent(ScheduledArtifactCheckStarting)
      val job = launch(blankMDC) {
        supervisorScope {
          repository.artifactsDueForCheck(checkMinAge, artifactBatchSize)
            .forEach { artifact ->
              try {
                withTimeout(artifactCheckConfig.timeoutDuration.toMillis()) {
                  launch {
                    artifactHandlers.forEach { handler ->
                      handler.handle(artifact)
                    }
                  }
                }
              } catch (e: TimeoutCancellationException) {
                log.error("Timed out checking artifact $artifact from ${artifact.deliveryConfigName}", e)
                publisher.publishEvent(ArtifactCheckTimedOut(artifact.name, artifact.deliveryConfigName))
              }
            }
        }
      }
      runBlocking { job.join() }
      publisher.publishEvent(ArtifactCheckComplete(Duration.between(startTime, clock.instant())))
      recordDuration(startTime, "artifact")
    }
  }

  @Scheduled(fixedDelayString = "\${keel.environment-verification.frequency:PT1S}")
  fun verifyEnvironments() {
    if (enabled.get()) {
      val startTime = clock.instant()
      publisher.publishEvent(ScheduledEnvironmentVerificationStarting)

      val job = launch(blankMDC) {
        supervisorScope {
          repository
            .nextEnvironmentsForVerification(verificationConfig.minAgeDuration, verificationBatchSize)
            .forEach {
              try {
                withTimeout(verificationConfig.timeoutDuration.toMillis()) {
                  launch {
                    try {
                      verificationRunner.runFor(it)
                    } catch (e: EnvironmentCurrentlyBeingActedOn) {
                      log.info("Couldn't verify ${it.version} in ${it.deliveryConfig.application}/${it.environmentName} because environment is currently being acted on", e.message)
                    }
                  }
                }
              } catch (e: TimeoutCancellationException) {
                log.error("Timed out verifying ${it.version} in ${it.deliveryConfig.application}/${it.environmentName}", e)
                publisher.publishEvent(VerificationTimedOut(it))
              }
            }
        }
      }

      runBlocking { job.join() }
      publisher.publishEvent(VerificationCheckComplete(Duration.between(startTime, clock.instant())))
      recordDuration(startTime, "verification")
    }
  }

  @Scheduled(fixedDelayString = "\${keel.environment-post-deploy.frequency:PT1S}")
  fun runPostDeployActions() {
    if (enabled.get()) {
      val startTime = clock.instant()
      publisher.publishEvent(ScheduledPostDeployActionRunStarting)

      val job = launch(blankMDC) {
        supervisorScope {
          repository
            .nextEnvironmentsForPostDeployAction(postDeployConfig.minAgeDuration, postDeployBatchSize)
            .forEach {
              try {
                withTimeout(postDeployConfig.timeoutDuration.toMillis()) {
                  launch {
                    postDeployActionRunner.runFor(it)
                  }
                }
              } catch (e: TimeoutCancellationException) {
                log.error("Timed out running post deploy actions on ${it.version} in ${it.deliveryConfig.application}/${it.environmentName}", e)
                publisher.publishEvent(PostDeployActionTimedOut(it))
              }
            }
        }
      }

      runBlocking { job.join() }
      publisher.publishEvent(PostDeployActionCheckComplete(Duration.between(startTime, clock.instant())))
      recordDuration(startTime, "postdeploy")
    }
  }

  // todo eb: remove this loop in favor of transitioning the [OrcaTaskMonitoringAgent] to a
  //  [LifecycleMonitor]
  @Scheduled(fixedDelayString = "\${keel.scheduled.agent.frequency:PT1M}")
  fun invokeAgent() {
    if (enabled.get()) {
      val startTime = clock.instant()
      agentLockRepository.agents.forEach {
        val agentName: String = it.javaClass.simpleName
        val lockAcquired = agentLockRepository.tryAcquireLock(agentName, it.lockTimeoutSeconds)
        if (lockAcquired) {
          runBlocking(blankMDC) {
            it.invokeAgent()
          }
          publisher.publishEvent(AgentInvocationComplete(Duration.between(startTime, clock.instant()), agentName))
        }
      }
      recordDuration(startTime, "agent")
    }
  }

  private fun recordDuration(startTime : Instant, type: String) =
    spectator.recordDurationPercentile("keel.scheduled.method.duration", clock, startTime, setOf(BasicTag("type", type)))

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
