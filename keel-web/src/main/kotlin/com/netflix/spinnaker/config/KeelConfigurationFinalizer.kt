package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.netflix.spinnaker.keel.actuation.ArtifactHandler
import com.netflix.spinnaker.keel.api.constraints.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.info.InstanceIdSupplier
import javax.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Component that wraps up keel configuration once all other beans have been instantiated.
 */
@Component
class KeelConfigurationFinalizer(
  private val baseImageCache: BaseImageCache? = null,
  private val instanceIdSupplier: InstanceIdSupplier,
  private val kinds: List<SupportedKind<*>> = emptyList(),
  private val resourceHandlers: List<ResourceHandler<*, *>> = emptyList(),
  private val constraintEvaluators: List<ConstraintEvaluator<*>> = emptyList(),
  private val artifactHandlers: List<ArtifactHandler> = emptyList(),
  private val objectMappers: List<ObjectMapper>
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @PostConstruct
  fun registerResourceSpecSubtypes() {
    resourceHandlers
      .map { it.supportedKind }
      .forEach { (kind, specClass) ->
        log.info("Registering ResourceSpec sub-type {}: {}", kind, specClass.simpleName)
        val namedType = NamedType(specClass, kind.toString())
        objectMappers.forEach { it.registerSubtypes(namedType) }
      }
  }

  @PostConstruct
  fun registerConstraintSubtypes() {
    constraintEvaluators
      .map { it.supportedType }
      .forEach { constraintType ->
        log.info("Registering Constraint sub-type {}: {}", constraintType.name, constraintType.type.simpleName)
        val namedType = NamedType(constraintType.type, constraintType.name)
        objectMappers.forEach { it.registerSubtypes(namedType) }
      }
  }

  @PostConstruct
  fun initialStatus() {
    sequenceOf(
        BaseImageCache::class to baseImageCache?.javaClass,
        InstanceIdSupplier::class to instanceIdSupplier.javaClass
    )
        .forEach { (type, implementation) ->
          log.info("{} implementation: {}", type.simpleName, implementation?.simpleName)
        }

    log.info("Supporting resource kinds: {}", kinds.joinToString { it.kind.toString() })
    log.info("Using resource handlers: {}", resourceHandlers.joinToString { it.name })
    log.info("Using artifact handlers: {}", artifactHandlers.joinToString { it.name })
  }
}
