package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.netflix.spinnaker.keel.actuation.ArtifactHandler
import com.netflix.spinnaker.keel.api.constraints.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.ec2.jackson.registerKeelEc2ApiModule
import com.netflix.spinnaker.keel.info.InstanceIdSupplier
import com.netflix.spinnaker.keel.resources.SpecMigrator
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
  private val specMigrators: List<SpecMigrator<*, *>> = emptyList(),
  private val constraintEvaluators: List<ConstraintEvaluator<*>> = emptyList(),
  private val artifactHandlers: List<ArtifactHandler> = emptyList(),
  private val artifactSuppliers: List<ArtifactSupplier<*, *>> = emptyList(),
  private val objectMappers: List<ObjectMapper>
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  // TODO: not sure if we can do this more dynamically
  @PostConstruct
  fun registerApiExtensionsWithObjectMappers() {
    objectMappers.forEach {
      it.registerKeelEc2ApiModule()
    }
  }

  @PostConstruct
  fun registerResourceSpecSubtypes() {
    (resourceHandlers.map { it.supportedKind } + specMigrators.map { it.input })
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
  fun resisterStatefulConstraintAttributeSubtypes() {
    constraintEvaluators
      .filterIsInstance<StatefulConstraintEvaluator<*, *>>()
      .map { it.attributeType }
      .forEach { attributeType ->
        log.info("Registering Constraint Attributes sub-type {}: {}", attributeType.name, attributeType.type.simpleName)
        val namedType = NamedType(attributeType.type, attributeType.name)
        objectMappers.forEach { it.registerSubtypes(namedType) }
      }
  }

  @PostConstruct
  fun registerArtifactPublisherSubtypes() {
    artifactSuppliers
      .map { it.supportedArtifact }
      .forEach { (name, artifactClass) ->
        log.info("Registering DeliveryArtifact sub-type {}: {}", name, artifactClass.simpleName)
        val namedType = NamedType(artifactClass, name)
        objectMappers.forEach { it.registerSubtypes(namedType) }
      }

    artifactSuppliers
      .map { it.supportedVersioningStrategy }
      .forEach { (name, strategyClass) ->
        log.info("Registering VersioningStrategy sub-type {}: {}", name, strategyClass.simpleName)
        val namedType = NamedType(strategyClass, name)
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

    log.info("Supported resources: {}", kinds.joinToString { it.kind.toString() })
    log.info("Supported artifacts: {}", artifactSuppliers.joinToString { it.supportedArtifact.name })
    log.info("Using resource handlers: {}", resourceHandlers.joinToString { it.name })
    log.info("Using artifact handlers: {}", artifactHandlers.joinToString { it.name })
  }
}
