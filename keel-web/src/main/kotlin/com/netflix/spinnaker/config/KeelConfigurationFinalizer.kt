package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.actuation.ArtifactHandler
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.api.support.ExtensionRegistry
import com.netflix.spinnaker.keel.api.support.extensionsOf
import com.netflix.spinnaker.keel.api.support.register
import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.ec2.jackson.registerKeelEc2ApiModule
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
  private val resourceHandlers: List<ResourceHandler<*, *>> = emptyList(),
  private val specMigrators: List<SpecMigrator<*, *>> = emptyList(),
  private val constraintEvaluators: List<ConstraintEvaluator<*>> = emptyList(),
  private val artifactHandlers: List<ArtifactHandler> = emptyList(),
  private val artifactSuppliers: List<ArtifactSupplier<*, *>> = emptyList(),
  private val objectMappers: List<ObjectMapper>,
  private val extensionRegistry: ExtensionRegistry
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
        extensionRegistry.register(specClass, kind.toString())
      }
  }

  @PostConstruct
  fun registerConstraintSubtypes() {
    constraintEvaluators
      .map { it.supportedType }
      .forEach { constraintType ->
        log.info("Registering Constraint sub-type {}: {}", constraintType.name, constraintType.type.simpleName)
        extensionRegistry.register(constraintType.type, constraintType.name)
      }
  }

  @PostConstruct
  fun resisterStatefulConstraintAttributeSubtypes() {
    constraintEvaluators
      .filterIsInstance<StatefulConstraintEvaluator<*, *>>()
      .map { it.attributeType }
      .forEach { attributeType ->
        log.info("Registering Constraint Attributes sub-type {}: {}", attributeType.name, attributeType.type.simpleName)
        extensionRegistry.register(attributeType.type, attributeType.name)
      }
  }

  @PostConstruct
  fun registerArtifactSupplierSubtypes() {
    artifactSuppliers
      .map { it.supportedArtifact }
      .forEach { (name, artifactClass) ->
        log.info("Registering DeliveryArtifact sub-type {}: {}", name, artifactClass.simpleName)
        extensionRegistry.register(artifactClass, name)
      }

    artifactSuppliers
      .map { it.supportedVersioningStrategy }
      .forEach { (name, strategyClass) ->
        log.info("Registering VersioningStrategy sub-type {}: {}", name, strategyClass.simpleName)
        extensionRegistry.register(strategyClass, name)
      }
  }

  @PostConstruct
  fun initialStatus() {
    sequenceOf(
      BaseImageCache::class to baseImageCache?.javaClass
    )
      .forEach { (type, implementation) ->
        log.info("{} implementation: {}", type.simpleName, implementation?.simpleName)
      }

    val kinds = extensionRegistry
      .extensionsOf<ResourceSpec>()
      .entries
      .map { SupportedKind(ResourceKind.parseKind(it.key), it.value) }

    log.info("Supported resources: {}", kinds.joinToString { it.kind.toString() })
    log.info("Supported artifacts: {}", artifactSuppliers.joinToString { it.supportedArtifact.name })
    log.info("Using resource handlers: {}", resourceHandlers.joinToString { it.name })
    log.info("Using artifact handlers: {}", artifactHandlers.joinToString { it.name })
  }
}
