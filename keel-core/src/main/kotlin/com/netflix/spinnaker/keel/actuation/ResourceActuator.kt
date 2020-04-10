package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.VersionedArtifactProvider
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceActuationVetoed
import com.netflix.spinnaker.keel.events.ResourceCheckError
import com.netflix.spinnaker.keel.events.ResourceCheckUnresolvable
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.logging.TracingSupport.Companion.withTracingContext
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.CannotResolveCurrentState
import com.netflix.spinnaker.keel.plugin.CannotResolveDesiredState
import com.netflix.spinnaker.keel.telemetry.ResourceCheckSkipped
import com.netflix.spinnaker.keel.veto.VetoEnforcer
import com.netflix.spinnaker.keel.veto.VetoResponse
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

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
      val coid = UUID.randomUUID().toString() // coroutine id for log messages to help debug #951
      val id = resource.id
      log.debug("checkResource $id [$coid]")
      val plugin = handlers.supporting(resource.kind)

      if (actuationPauser.isPaused(resource)) {
        log.debug("Actuation for resource {} is paused, skipping checks [$coid]", id)
        publisher.publishEvent(ResourceCheckSkipped(resource.kind, id, "ActuationPaused"))
        return@withTracingContext
      }

      if (plugin.actuationInProgress(resource)) {
        log.debug("Actuation for resource {} is already running, skipping checks [$coid]", id)
        publisher.publishEvent(ResourceCheckSkipped(resource.kind, id, "ActuationInProgress"))
        return@withTracingContext
      }

      try {
        val (desired, current) = plugin.resolve(resource, coid)
        val diff = DefaultResourceDiff(desired, current)
        if (diff.hasChanges()) {
          diffFingerprintRepository.store(id, diff)
        }

        val response = vetoEnforcer.canCheck(resource)
        if (!response.allowed) {
          /**
           * [VersionedArtifactProvider] is a special [resource] sub-type. When a veto response sets
           * [VetoResponse.vetoArtifact] and the resource under evaluation is of type
           * [VersionedArtifactProvider], blacklist the desired artifact version from the environment
           * containing [resource]. This ensures that the environment will be fully restored to
           * a prior good-state.
           */
          if (response.vetoArtifact && resource.spec is VersionedArtifactProvider) {
            try {
              val versionedArtifact = when (desired) {
                is Map<*, *> -> {
                  if (desired.size > 0) {
                    (desired as Map<String, VersionedArtifactProvider>).values.first()
                  } else {
                    null
                  }
                }
                is VersionedArtifactProvider -> desired
                else -> null
              }?.let {
                it.completeVersionedArtifactOrNull()
              }

              if (versionedArtifact != null) {
                with(versionedArtifact) {
                  val deliveryConfig = deliveryConfigRepository.deliveryConfigFor(resource.id)
                  val environment = deliveryConfig.environmentFor(resource)?.name
                    ?: error("Failed to find environment for ${resource.id} in deliveryConfig ${deliveryConfig.name} " +
                      "while attempting to veto artifact $artifactType:$artifactName version $artifactVersion")
                  val artifact = deliveryConfig.matchingArtifactByName(versionedArtifact.artifactName, artifactType)
                    ?: error("Artifact $artifactType:$artifactName not found in delivery config ${deliveryConfig.name}")

                  artifactRepository.markAsVetoedIn(
                    deliveryConfig = deliveryConfig,
                    artifact = artifact,
                    version = artifactVersion,
                    targetEnvironment = environment)
                  // TODO: emit event + metric
                }
              }
            } catch (e: Exception) {
              log.warn("Failed to veto presumed bad artifact version for ${resource.id} [$coid]", e)
              // TODO: emit metric
            }
          }
          log.debug("Skipping actuation for resource {} because it was vetoed: {} [$coid]", id, response.message)
          publisher.publishEvent(ResourceCheckSkipped(resource.kind, id, response.vetoName))
          publishVetoedEvent(response, resource)
          return@withTracingContext
        }

        log.debug("Checking resource {} [$coid]", id)

        when {
          current == null -> {
            log.warn("Resource {} is missing [$coid]", id)
            publisher.publishEvent(ResourceMissing(resource, clock))

            plugin.create(resource, diff)
              .also { tasks ->
                publisher.publishEvent(ResourceActuationLaunched(resource, plugin.name, tasks, clock))
              }
          }
          diff.hasChanges() -> {
            log.warn("Resource {} is invalid [$coid]", id)
            log.info("Resource {} delta: {} [$coid]", id, diff.toDebug())
            publisher.publishEvent(ResourceDeltaDetected(resource, diff.toDeltaJson(), clock))

            plugin.update(resource, diff)
              .also { tasks ->
                publisher.publishEvent(ResourceActuationLaunched(resource, plugin.name, tasks, clock))
              }
          }
          else -> {
            log.info("Resource {} is valid [$coid]", id)
            // TODO: not sure this logic belongs here
            val lastEvent = resourceRepository.lastEvent(id)
            if (lastEvent is ResourceDeltaDetected || lastEvent is ResourceActuationLaunched) {
              publisher.publishEvent(ResourceDeltaResolved(resource, clock))
            } else {
              publisher.publishEvent(ResourceValid(resource, clock))
            }
          }
        }
      } catch (e: ResourceCurrentlyUnresolvable) {
        log.warn("Resource check for {} failed (hopefully temporarily) due to {} [$coid]", id, e.message)
        publisher.publishEvent(ResourceCheckUnresolvable(resource, e, clock))
      } catch (e: Exception) {
        log.error("Resource check for $id failed [$coid]", e)
        publisher.publishEvent(ResourceCheckError(resource, e, clock))
      }
    }
  }

  private suspend fun <T : Any> ResourceHandler<*, T>.resolve(resource: Resource<out ResourceSpec>, coid: String): Pair<T, T?> =
    supervisorScope {
      val desired = async {
        try {
          log.debug("before desired: ${resource.id}: [$coid]")
          val result = desired(resource)
          log.debug("after desired: ${resource.id}: [$coid]")
          result
        } catch (e: ResourceCurrentlyUnresolvable) {
          throw e
        } catch (e: Throwable) {
          throw CannotResolveDesiredState(resource.id, e)
        }
      }

      // Trying single thread context to see if it works around https://github.com/spinnaker/keel/issues/951
      val current = async(newSingleThreadContext("actuation.resolve.current")) {
        try {
          log.debug("before current: ${resource.id}: [$coid]")
          val result = current(resource)
          log.debug("after current: ${resource.id}: [$coid]")
          result
        } catch (e: Throwable) {
          throw CannotResolveCurrentState(resource.id, e)
        }
      }

      // make await() calls on separate lines so that a stack trace will indicate which one timed out
      val d = desired.await()
      val c = current.await()
      d to c
    }

  /**
   * We want a specific status for specific types of vetos. This function publishes the
   * right event based on which veto said no.
   */
  private fun publishVetoedEvent(response: VetoResponse, resource: Resource<*>) =
    when {
      response.vetoName == "UnhappyVeto" -> {
        // don't publish an event, we want the status to stay as "unhappy" for clarity
      }
      else -> publisher.publishEvent(
        ResourceActuationVetoed(
          resource.kind,
          resource.id,
          resource.spec.application,
          response.message,
          clock.instant()))
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
