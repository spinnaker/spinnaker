package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.VersionedArtifactProvider
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceActuationVetoed
import com.netflix.spinnaker.keel.events.ResourceCheckError
import com.netflix.spinnaker.keel.events.ResourceCheckUnresolvable
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
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
import com.netflix.spinnaker.keel.telemetry.ResourceCheckSkipped
import com.netflix.spinnaker.keel.veto.VetoEnforcer
import com.netflix.spinnaker.keel.veto.VetoResponse
import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.exceptions.UserException
import java.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

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
  private val clock: Clock
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  suspend fun <T : ResourceSpec> checkResource(resource: Resource<T>) {
    withTracingContext(resource) {
      val id = resource.id
      log.debug("checkResource $id")
      val plugin = handlers.supporting(resource.kind)

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

      try {
        val (desired, current) = plugin.resolve(resource)
        val diff = DefaultResourceDiff(desired, current)
        if (diff.hasChanges()) {
          diffFingerprintRepository.store(id, diff)
        } else {
          diffFingerprintRepository.clear(id)
        }

        val response = vetoEnforcer.canCheck(resource)
        if (!response.allowed) {
          /**
           * [VersionedArtifactProvider] is a special [resource] sub-type. When a veto response sets
           * [VetoResponse.vetoArtifact] and the resource under evaluation is of type
           * [VersionedArtifactProvider], disallow the desired artifact version from being deployed to
           * the environment containing [resource]. This ensures that the environment will be fully restored to
           * a prior good-state.
           */
          if (response.vetoArtifact && resource.spec is VersionedArtifactProvider) {
            try {
              val versionedArtifact = when (desired) {
                is Map<*, *> -> {
                  if (desired.size > 0) {
                    @Suppress("UNCHECKED_CAST")
                    (desired as Map<String, VersionedArtifactProvider>).values.first()
                  } else {
                    null
                  }
                }
                is VersionedArtifactProvider -> desired
                else -> null
              }?.completeVersionedArtifactOrNull()

              if (versionedArtifact != null) {
                with(versionedArtifact) {
                  val deliveryConfig = deliveryConfigRepository.deliveryConfigFor(resource.id)
                  val environment = deliveryConfig.environmentFor(resource)?.name
                    ?: error(
                      "Failed to find environment for ${resource.id} in deliveryConfig ${deliveryConfig.name} " +
                        "while attempting to veto artifact $artifactType:$artifactName version $artifactVersion"
                    )
                  val artifact = deliveryConfig.matchingArtifactByName(versionedArtifact.artifactName, artifactType)
                    ?: error("Artifact $artifactType:$artifactName not found in delivery config ${deliveryConfig.name}")

                  artifactRepository.markAsVetoedIn(
                    deliveryConfig = deliveryConfig,
                    veto = EnvironmentArtifactVeto(
                      reference = artifact.reference,
                      version = artifactVersion,
                      targetEnvironment = environment,
                      vetoedBy = "Spinnaker",
                      comment = "Automatically marked as bad because multiple deployments of this version failed."
                    )
                  )
                  // TODO: emit event + metric
                }
              }
            } catch (e: Exception) {
              log.warn("Failed to veto presumed bad artifact version for ${resource.id}", e)
              // TODO: emit metric
            }
          }
          log.debug("Skipping actuation for resource {} because it was vetoed: {}", id, response.message)
          publisher.publishEvent(ResourceCheckSkipped(resource.kind, id, response.vetoName))
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

        log.debug("Checking resource {}", id)

        // todo eb: add support for plugins to look at a diff and say "I can't fix this"
        // todo eb: emit event for ^ with custom message provided by the plugin

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
                publisher.publishEvent(ResourceActuationLaunched(resource, plugin.name, tasks, clock))
                diffFingerprintRepository.markActionTaken(id)
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
      } catch (e: ResourceCurrentlyUnresolvable) {
        log.warn("Resource check for {} failed (hopefully temporarily) due to {}", id, e.message)
        publisher.publishEvent(ResourceCheckUnresolvable(resource, e, clock))
      } catch (e: Exception) {
        log.error("Resource check for $id failed", e)
        publisher.publishEvent(ResourceCheckError(resource, e.toSpinnakerException(), clock))
      }
    }
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

  private suspend fun <T : Any> ResourceHandler<*, T>.resolve(resource: Resource<ResourceSpec>): Pair<T, T?> =
    supervisorScope {
      val desired = async {
        try {
          desired(resource)
        } catch (e: ResourceCurrentlyUnresolvable) {
          throw e
        } catch (e: Throwable) {
          throw CannotResolveDesiredState(resource.id, e)
        }
      }

      val current = async {
        try {
          current(resource)
        } catch (e: Throwable) {
          throw CannotResolveCurrentState(resource.id, e)
        }
      }

      // make await() calls on separate lines so that a stack trace will indicate which one timed out
      val d = desired.await()
      val c = current.await()
      d to c
    }

  private fun DeliveryConfig.environmentFor(resource: Resource<*>): Environment? =
    environments.firstOrNull { it.resources.contains(resource) }

  // These extensions get round the fact tht we don't know the spec type of the resource from
  // the repository. I don't want the `ResourceHandler` interface to be untyped though.
  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.desired(
    resource: Resource<*>
  ): R =
    desired(resource as Resource<S>)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.current(
    resource: Resource<*>
  ): R? =
    current(resource as Resource<S>)

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
