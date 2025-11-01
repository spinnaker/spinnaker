package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.actuation.ArtifactHandler
import com.netflix.spinnaker.keel.api.ClusterDeployStrategy
import com.netflix.spinnaker.keel.api.Highlander
import com.netflix.spinnaker.keel.api.NoStrategy
import com.netflix.spinnaker.keel.api.RedBlack
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.RollingPush
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.api.constraints.StatelessConstraintEvaluator
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Action
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.plugins.PostDeployActionHandler
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.api.support.ExtensionRegistry
import com.netflix.spinnaker.keel.api.support.extensionsOf
import com.netflix.spinnaker.keel.api.support.register
import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.ec2.jackson.registerEc2Subtypes
import com.netflix.spinnaker.keel.ec2.jackson.registerKeelEc2ApiModule
import com.netflix.spinnaker.keel.resources.SpecMigrator
import com.netflix.spinnaker.keel.titus.jackson.registerKeelTitusApiModule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct

/**
 * Component that wraps up keel configuration once all other beans have been instantiated.
 */
@Component
class KeelConfigurationFinalizer(
  private val baseImageCache: BaseImageCache? = null,
  private val resourceHandlers: List<ResourceHandler<*, *>> = emptyList(),
  private val specMigrators: List<SpecMigrator<*, *>> = emptyList(),
  private val constraintEvaluators: List<ConstraintEvaluator<*>> = emptyList(),
  private val verificationEvaluators: List<VerificationEvaluator<*>> = emptyList(),
  private val postDeployActionHandlers: List<PostDeployActionHandler<*>> = emptyList(),
  private val artifactHandlers: List<ArtifactHandler> = emptyList(),
  private val artifactSuppliers: List<ArtifactSupplier<*, *>> = emptyList(),
  private val objectMappers: List<ObjectMapper>,
  private val extensionRegistry: ExtensionRegistry,
  private val resolvers: List<Resolver<*>>
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  // TODO: not sure if we can do this more dynamically
  @PostConstruct
  fun registerApiExtensionsWithObjectMappers() {
    // Registering sub-types with the extension registry is redundant with the call to
    // registerKeelEc2ApiModule below, as far as object mappers go, but needed for the schema generator.
    extensionRegistry.registerEc2Subtypes()
    objectMappers.forEach {
      it.registerKeelEc2ApiModule()
      it.registerKeelTitusApiModule()
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
  fun registerVerificationSubtypes() {
    verificationEvaluators
      .map { it.supportedVerification }
      .forEach { (type, implementingClass) ->
        log.info("Registering Verification sub-type {}: {}", type, implementingClass.simpleName)
        extensionRegistry.register(implementingClass, type)
      }
  }

  @PostConstruct
  fun registerPostDeploySubtypes() {
    postDeployActionHandlers
      .map { it.supportedType }
      .forEach { postDeployActionType ->
        log.info("Registering post deploy action runner sub-type {}: {}", postDeployActionType.name, postDeployActionType.type.simpleName)
        extensionRegistry.register(postDeployActionType.type, postDeployActionType.name)
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

    constraintEvaluators
      .filterIsInstance<StatelessConstraintEvaluator<*, *>>()
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
      .map { it.supportedSortingStrategy }
      .forEach { (name, strategyClass) ->
        log.info("Registering SortingStrategy sub-type {}: {}", name, strategyClass.simpleName)
        extensionRegistry.register(strategyClass, name)
      }
  }

  @PostConstruct
  fun registerClusterDeployStrategySubtypes() {
    extensionRegistry.register<ClusterDeployStrategy>(RedBlack::class.java, "red-black")
    extensionRegistry.register<ClusterDeployStrategy>(Highlander::class.java, "highlander")
    extensionRegistry.register<ClusterDeployStrategy>(NoStrategy::class.java, "none")
    extensionRegistry.register<ClusterDeployStrategy>(RollingPush::class.java, "rolling-push")
  }

  @PostConstruct
  fun registerListenerActionSubtypes() {
    with(extensionRegistry) {
      register<Action>(Action.RedirectAction::class.java, "redirect")
      register<Action>(Action.ForwardAction::class.java, "forward")
      register<Action>(Action.AuthenticateOidcAction::class.java, "authenticate-oidc")
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
    log.info("Using verification evaluators: {}", verificationEvaluators.joinToString { it.javaClass.simpleName })
    log.info("Using post deploy action runners: {}", postDeployActionHandlers.joinToString { it.javaClass.simpleName })
    log.info("Using resolvers: {}", resolvers.joinToString { it.name })
  }
}
