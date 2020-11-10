package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.CompleteVersionedArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.VersionedArtifactProvider
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.plugins.ActionDecision
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.PromotionStatus.DEPLOYING
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceActuationVetoed
import com.netflix.spinnaker.keel.events.ResourceCheckError
import com.netflix.spinnaker.keel.events.ResourceCheckUnresolvable
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceDiffNotActionable
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.ResourceTaskFailed
import com.netflix.spinnaker.keel.events.ResourceTaskSucceeded
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.logging.TracingSupport.Companion.withTracingContext
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.CannotResolveCurrentState
import com.netflix.spinnaker.keel.plugin.CannotResolveDesiredState
import com.netflix.spinnaker.keel.plugin.ResourceResolutionException
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionVetoed
import com.netflix.spinnaker.keel.telemetry.ResourceCheckSkipped
import com.netflix.spinnaker.keel.veto.VetoEnforcer
import com.netflix.spinnaker.keel.veto.VetoResponse
import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.exceptions.UserException
import com.newrelic.api.agent.Trace
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import org.springframework.core.env.Environment as SpringEnvironment

/**
 * The core component in keel responsible for resource state monitoring and actuation.
 *
 * The [checkResource] method of this class is called periodically by [CheckScheduler]
 * to check on the current state of a specific [Resource] via the [ResourceHandler.current] method
 * of the corresponding [ResourceHandler] plugin, compare that with the desired state obtained
 * from [ResourceHandler.desired], and finally call the appropriate resource CRUD method on
 * the [ResourceHandler] if differences are detected.
 */
@Component
class ResourceActuator(
  private val resourceRepository: ResourceRepository,
  private val artifactRepository: ArtifactRepository,
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val diffFingerprintRepository: DiffFingerprintRepository,
  private val handlers: List<ResourceHandler<*, *>>,
  private val actuationPauser: ActuationPauser,
  private val vetoEnforcer: VetoEnforcer,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock,
  private val springEnv: SpringEnvironment
) {
  companion object {
    private val asyncExecutor: Executor = Executors.newCachedThreadPool()
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @Trace(dispatcher=true)
  suspend fun <T : ResourceSpec> checkResource(resource: Resource<T>) {
    withTracingContext(resource) {
      val id = resource.id
      try {
        val plugin = handlers.supporting(resource.kind)
        val deliveryConfig = deliveryConfigRepository.deliveryConfigFor(resource.id)
        val environment = checkNotNull(deliveryConfig.environmentFor(resource)) {
          "Failed to find environment for ${resource.id} in deliveryConfig ${deliveryConfig.name}"
        }

        log.debug("Checking resource $id in environment ${environment.name} of application ${deliveryConfig.application}")
        
        if (actuationPauser.isPaused(resource)) {
          log.debug("Actuation for resource {} is paused, skipping checks", id)
          publisher.publishEvent(ResourceCheckSkipped(resource.kind, id, "ActuationPaused"))
          return@withTracingContext
        }

        if (plugin.actuationInProgress(resource)) {
          log.debug("Actuation for resource {} is already running, skipping checks", id)
          publisher.publishEvent(ResourceCheckSkipped(resource.kind, id, "ActuationInProgress"))
          return@withTracingContext
        }

        if (deliveryConfig.isPromotionCheckStale()) {
          log.debug("Environments check for {} is stale, skipping checks", deliveryConfig.name)
          publisher.publishEvent(ResourceCheckSkipped(resource.kind, id, "PromotionCheckStale"))
          return@withTracingContext
        }

        val (desired, current) = plugin.resolve(resource)
        val diff = DefaultResourceDiff(desired, current)
        if (diff.hasChanges()) {
          log.debug("Storing diff fingerprint for resource {} delta: {}", id, diff.toDebug())
          diffFingerprintRepository.store(id, diff)
        } else {
          log.debug("Clearing diff fingerprint for resource {} (current == desired)", id)
          diffFingerprintRepository.clear(id)
        }

        val response = vetoEnforcer.canCheck(resource)
        if (!response.allowed) {
          handleArtifactVetoing(response, resource, deliveryConfig, environment, desired)
          log.debug("Skipping actuation for resource {} because it was vetoed: {}", resource.id, response.message)
          publisher.publishEvent(ResourceCheckSkipped(resource.kind, resource.id, response.vetoName))
          publisher.publishEvent(
            ResourceActuationVetoed(
              resource.kind,
              resource.id,
              resource.spec.application,
              response.message,
              response.vetoName,
              response.suggestedStatus,
              clock.instant()
            )
          )
          return@withTracingContext
        }

        val decision = plugin.willTakeAction(resource, diff)
        if (decision.willAct) {
          when {
            current == null -> {
              log.warn("Resource {} is missing", id)
              publisher.publishEvent(ResourceMissing(resource, clock))

              plugin.create(resource, diff)
                .also { tasks ->
                  publisher.publishEvent(ResourceActuationLaunched(resource, plugin.name, tasks, clock))
                  diffFingerprintRepository.markActionTaken(id)
                }
            }
            diff.hasChanges() -> {
              log.warn("Resource {} is invalid", id)
              log.info("Resource {} delta: {}", id, diff.toDebug())
              publisher.publishEvent(ResourceDeltaDetected(resource, diff.toDeltaJson(), clock))

              plugin.update(resource, diff)
                .also { tasks ->
                  if (tasks.isNotEmpty()) {
                    publisher.publishEvent(ResourceActuationLaunched(resource, plugin.name, tasks, clock))
                    diffFingerprintRepository.markActionTaken(id)
                  }
                }
            }
            else -> {
              log.info("Resource {} is valid", id)
              val lastEvent = resourceRepository.lastEvent(id)
              when (lastEvent) {
                is ResourceActuationLaunched -> log.debug("waiting for actuating task to be completed") // do nothing and wait
                is ResourceDeltaDetected, is ResourceTaskSucceeded, is ResourceTaskFailed -> {
                  // if a delta was detected and a task wasn't launched, the delta is resolved
                  // if a task was launched and it completed, either successfully or not, the delta is resolved
                  publisher.publishEvent(ResourceDeltaResolved(resource, clock))
                }
                else -> publisher.publishEvent(ResourceValid(resource, clock))
              }
            }
          }
        } else {
          log.warn("Resource {} skipped because it can't be fixed: {} (diff: {})", id, decision.message, diff.toDeltaJson())
          if (diff.hasChanges()) {
            publisher.publishEvent(ResourceDeltaDetected(resource, diff.toDeltaJson(), clock))
          }
          publisher.publishEvent(ResourceDiffNotActionable(resource, decision.message))
        }
      } catch (e: ResourceCurrentlyUnresolvable) {
        log.warn("Resource check for {} failed (hopefully temporarily) due to {}", id, e.message)
        publisher.publishEvent(ResourceCheckUnresolvable(resource, e, clock))
      } catch (e: Exception) {
        log.error("Resource check for $id failed", e)
        publisher.publishEvent(ResourceCheckError(resource, e.toSpinnakerException(), clock))
      }
    }
  }

  /**
   * [VersionedArtifactProvider] is a special [resource] sub-type. When a veto response sets
   * [VetoResponse.vetoArtifact], the resource under evaluation is of type
   * [VersionedArtifactProvider], and the version has never before been deployed successfully to an environment,
   * disallow the desired artifact version from being deployed to
   * the environment containing [resource]. This ensures that the environment will be fully restored to
   * a prior good-state.
   *
   * This method can override a veto's request to veto an artifact. In this method we have more
   * information about the diff and the artifact version, and so we can make a better decision about
   * whether or not to veto an artifact version
   *
   * If the version has previously been deployed successfully do not veto the artifact version because
   * we do not want to veto the artifact version in the case of a bad config change or other downstream
   * problem. Rolling back most likely will not help in these cases and it will probably cause confusion.
   */
  private fun handleArtifactVetoing(
    response: VetoResponse,
    resource: Resource<*>,
    deliveryConfig: DeliveryConfig,
    environment: Environment,
    desired: Any?
  ) {
    if (response.vetoArtifact && resource.spec is VersionedArtifactProvider) {
      try {
        val versionedArtifact = desired.findArtifact()

        if (versionedArtifact != null) {
          val artifact = deliveryConfig.matchingArtifactByName(versionedArtifact.artifactName, versionedArtifact.artifactType)
            ?: error("Artifact ${versionedArtifact.artifactType}:${versionedArtifact.artifactName} not found in delivery config ${deliveryConfig.name}")

          val promotionStatus = artifactRepository.getArtifactPromotionStatus(
            deliveryConfig, artifact, versionedArtifact.artifactVersion, environment.name)

          if (promotionStatus == DEPLOYING) {
            artifactRepository.markAsVetoedIn(
              deliveryConfig = deliveryConfig,
              veto = EnvironmentArtifactVeto(
                reference = artifact.reference,
                version = versionedArtifact.artifactVersion,
                targetEnvironment = environment.name,
                vetoedBy = "Spinnaker",
                comment = "Automatically marked as bad because multiple deployments of this version failed and none have ever succeeded."
              )
            )
            log.info(
              "Vetoing artifact version {} of artifact {} for config {} and env {} because multiple deploys failed",
              versionedArtifact,
              artifact.reference + ":" + artifact.type,
              deliveryConfig.name,
              environment.name
            )
            publisher.publishEvent(ArtifactVersionVetoed(resource.application))
          } else {
            log.info(
              "Not vetoing artifact version {} of artifact {} for config {} and env {} because it's not currently deploying",
              versionedArtifact,
              artifact.reference + ":" + artifact.type,
              deliveryConfig.name,
              environment.name
            )
          }
        }
      } catch (e: Exception) {
        log.warn("Failed to veto presumed bad artifact version for ${resource.id}", e)
        // TODO: emit metric
      }
    }
  }

  private fun Any?.findArtifact() : CompleteVersionedArtifact? =
    when (this) {
      is Map<*, *> -> {
        if (this.size > 0) {
          @Suppress("UNCHECKED_CAST")
          (this as Map<String, VersionedArtifactProvider>).values.first()
        } else {
          null
        }
      }
      is VersionedArtifactProvider -> this
      else -> null
    }?.completeVersionedArtifactOrNull()


  private fun DeliveryConfig.isPromotionCheckStale(): Boolean {
    val age = Duration.between(
      deliveryConfigRepository.deliveryConfigLastChecked(this),
      clock.instant()
    )
    return age > Duration.ofMinutes(5)
  }

  private fun Exception.toSpinnakerException(): SpinnakerException =
    when (this) {
      is ResourceResolutionException -> when (cause) {
        is UserException, is SystemException -> cause
        else -> this
      }
      is UserException, is SystemException -> this
      else -> SystemException(this)
    } as SpinnakerException

  private suspend fun <T : Any> ResourceHandler<*, T>.resolve(resource: Resource<ResourceSpec>): Pair<T, T?> {
    val isKotlin = this.javaClass.isKotlinClass
    return supervisorScope {
      val desired = if (isKotlin) {
        async {
          try {
            desired(resource)
          } catch (e: ResourceCurrentlyUnresolvable) {
            throw e
          } catch (e: Throwable) {
            throw CannotResolveDesiredState(resource.id, e)
          }
        }
      } else {
        // for Java compatibility
        desiredAsync(resource, asyncExecutor).asDeferred()
      }

      val current = if (isKotlin) {
        async {
          try {
            current(resource)
          } catch (e: Throwable) {
            throw CannotResolveCurrentState(resource.id, e)
          }
        }
      } else {
        // for Java compatibility
        currentAsync(resource, asyncExecutor).asDeferred()
      }

      // make await() calls on separate lines so that a stack trace will indicate which one timed out
      val d = desired.await()
      val c = current.await()
      d to c
    }
  }

  private fun DeliveryConfig.environmentFor(resource: Resource<*>): Environment? =
    environments.firstOrNull {
      it.resources
        .map { r -> r.id }
        .contains(resource.id)
    }

  private val Class<*>.isKotlinClass: Boolean
    get() = this.declaredAnnotations.any {
      it.annotationClass.qualifiedName == "kotlin.Metadata"
    }

  // These extensions get round the fact tht we don't know the spec type of the resource from
  // the repository. I don't want the `ResourceHandler` interface to be untyped though.
  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.desired(
    resource: Resource<*>
  ): R =
    desired(resource as Resource<S>)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.desiredAsync(
    resource: Resource<*>, executor: Executor
  ): CompletableFuture<R> =
    desiredAsync(resource as Resource<S>, executor)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.current(
    resource: Resource<*>
  ): R? =
    current(resource as Resource<S>)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.currentAsync(
    resource: Resource<*>, executor: Executor
  ): CompletableFuture<R?> =
    currentAsync(resource as Resource<S>, executor)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.willTakeAction(
    resource: Resource<*>,
    resourceDiff: ResourceDiff<*>
  ): ActionDecision =
    willTakeAction(resource as Resource<S>, resourceDiff as ResourceDiff<R>)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.create(
    resource: Resource<*>,
    resourceDiff: ResourceDiff<*>
  ): List<Task> =
    create(resource as Resource<S>, resourceDiff as ResourceDiff<R>)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.update(
    resource: Resource<*>,
    resourceDiff: ResourceDiff<*>
  ): List<Task> =
    update(resource as Resource<S>, resourceDiff as ResourceDiff<R>)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec> ResourceHandler<S, *>.actuationInProgress(
    resource: Resource<*>
  ): Boolean =
    actuationInProgress(resource as Resource<S>)
  // end type coercing extensions
}
