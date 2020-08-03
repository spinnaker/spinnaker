package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.anyStateful
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.persistence.KeelRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * This class is responsible for running constraints and queueing them for approval.
 * Approval into an environment is the responsibility of the [EnvironmentPromotionChecker].
 */
@Component
class EnvironmentConstraintRunner(
  private val repository: KeelRepository,
  private val constraints: List<ConstraintEvaluator<*>>
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val implicitConstraints: List<ConstraintEvaluator<*>> = constraints.filter { it.isImplicit() }
  private val explicitConstraints: List<ConstraintEvaluator<*>> = constraints - implicitConstraints

  // constraints that are only run if they are defined in a delivery config
  private val statefulEvaluators: List<ConstraintEvaluator<*>> = explicitConstraints
    .filterIsInstance<StatefulConstraintEvaluator<*, *>>()
  private val statelessEvaluators = explicitConstraints - statefulEvaluators

  // constraints that run for every environment in a delivery config but aren't shown to the user.
  private val implicitStatefulEvaluators: List<ConstraintEvaluator<*>> = implicitConstraints
    .filterIsInstance<StatefulConstraintEvaluator<*, *>>()
  private val implicitStatelessEvaluators: List<ConstraintEvaluator<*>> = implicitConstraints - implicitStatefulEvaluators

  /**
   * Checks the environment and determines the version that should be approved,
   * or null if there is no version that passes the constraints for an env + artifact combo.
   * Queues that version for approval if it exists.
   */
  fun checkEnvironment(
    envContext: EnvironmentContext
  ) {
    val pendingVersionsToCheck: MutableSet<String> =
      when (envContext.environment.constraints.anyStateful) {
        true -> repository
          .pendingConstraintVersionsFor(envContext.deliveryConfig.name, envContext.environment.name)
          .filter { envContext.versions.contains(it) }
          .toMutableSet()
        false -> mutableSetOf()
      }

    checkConstraints(
      envContext,
      pendingVersionsToCheck
    )

    /*
     * If there are pending constraints for prior versions, that we didn't recheck in the process of
     * finding the latest version above, recheck in ascending version order
     * so they can be timed out, failed, or approved.
     */
    handleOlderPendingVersions(envContext, pendingVersionsToCheck)
  }

  /**
   * Looks at all versions for an environment, and determines the latest version that passes constraints,
   * or null if none pass.
   *
   * In the process we check and evaluate constraints for each version.
   *
   * If a version passes all constraints it is queued for approval.
   */
  private fun checkConstraints(
    envContext: EnvironmentContext,
    pendingVersionsToCheck: MutableSet<String>
  ) {
    var version: String? = null
    var versionIsPending = false
    val vetoedVersions: Set<String> = envContext.vetoedVersions

    version = envContext.versions
      .filterNot { vetoedVersions.contains(it) }
      .firstOrNull { v ->
        pendingVersionsToCheck.remove(v) // remove to indicate we are rechecking this version
        /**
         * Only check stateful evaluators if all stateless evaluators pass. We don't
         * want to request judgement or deploy a canary for artifacts that aren't
         * deployed to a required environment or outside of an allowed time.
         */
        val passesConstraints =
          checkStatelessConstraints(envContext.artifact, envContext.deliveryConfig, v, envContext.environment) &&
            checkStatefulConstraints(envContext.artifact, envContext.deliveryConfig, v, envContext.environment)

        if (envContext.environment.constraints.anyStateful) {
          versionIsPending = repository
            .constraintStateFor(envContext.deliveryConfig.name, envContext.environment.name, v)
            .any { it.status == ConstraintStatus.PENDING }
        }

        // select either the first version that passes all constraints,
        // or the first version where stateful constraints are pending.
        // so that we don't roll back while constraints are evaluating for a version
        passesConstraints || versionIsPending
      }
    if (version != null && !versionIsPending) {
      // we've selected a version that passes all constraints, queue it for approval
      queueForApproval(envContext.deliveryConfig, envContext.artifact, version, envContext.environment.name)
    }
  }

  /**
   * Re-checks older versions with pending stateful constraints to see if they can be approved,
   * queues them for approval if they pass
   */
  private fun handleOlderPendingVersions(
    envContext: EnvironmentContext,
    pendingVersionsToCheck: MutableSet<String>
  ) {
    log.debug("pendingVersionsToCheck: [$pendingVersionsToCheck] of artifact ${envContext.artifact.name} for environment ${envContext.environment.name} ")
    pendingVersionsToCheck
      .sortedWith(envContext.artifact.versioningStrategy.comparator.reversed()) // oldest first
      .forEach { version ->
        val passesConstraints =
          checkStatelessConstraints(envContext.artifact, envContext.deliveryConfig, version, envContext.environment) &&
            checkStatefulConstraints(envContext.artifact, envContext.deliveryConfig, version, envContext.environment)

        if (passesConstraints) {
          queueForApproval(envContext.deliveryConfig, envContext.artifact, version, envContext.environment.name)
        }
      }
  }

  /**
   * A container to hold all the context we need for evaluating environment constraints
   */
  data class EnvironmentContext(
    val deliveryConfig: DeliveryConfig,
    val environment: Environment,
    val artifact: DeliveryArtifact,
    val versions: List<String>,
    val vetoedVersions: Set<String>
  ) {
    fun hasNoConstraints(): Boolean =
      environment.constraints.isEmpty()
  }

  /**
   * Queues a version for approval if it's not already approved in the environment.
   * [EnvironmentPromotionChecker] handles actually approving a version for an environment.
   */
  private fun queueForApproval(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val latestVersion = repository
      .latestVersionApprovedIn(deliveryConfig, artifact, targetEnvironment)
    if (latestVersion != version) {
      log.debug("Queueing version $version of ${artifact.type} artifact ${artifact.name} in environment $targetEnvironment for approval")
      repository.queueAllConstraintsApproved(deliveryConfig.name, targetEnvironment, version, artifact.reference)
    }
  }

  fun checkStatelessConstraints(
    artifact: DeliveryArtifact,
    deliveryConfig: DeliveryConfig,
    version: String,
    environment: Environment
  ): Boolean =
    checkConstraintForEveryEnvironment(implicitStatelessEvaluators, artifact, deliveryConfig, version, environment) &&
      checkConstraintWhenSpecified(statelessEvaluators, artifact, deliveryConfig, version, environment)

  fun checkStatefulConstraints(
    artifact: DeliveryArtifact,
    deliveryConfig: DeliveryConfig,
    version: String,
    environment: Environment
  ): Boolean =
    checkConstraintForEveryEnvironment(implicitStatefulEvaluators, artifact, deliveryConfig, version, environment) &&
      checkConstraintWhenSpecified(statefulEvaluators, artifact, deliveryConfig, version, environment)

  /**
   * Checks constraints for a list of evaluators.
   * Evaluates the constraint for every environment passed in.
   * @return true if all constraints pass
   */
  private fun checkConstraintForEveryEnvironment(
    evaluators: List<ConstraintEvaluator<*>>,
    artifact: DeliveryArtifact,
    deliveryConfig: DeliveryConfig,
    version: String,
    environment: Environment
  ): Boolean =
    evaluators.all { evaluator ->
      evaluator.canPromote(artifact, version, deliveryConfig, environment)
    }

  /**
   * Checks constraints for a list of evaluators.
   * Evaluates the constraint only if it's defined on the environment.
   * @return true if all constraints pass
   */
  private fun checkConstraintWhenSpecified(
    evaluators: List<ConstraintEvaluator<*>>,
    artifact: DeliveryArtifact,
    deliveryConfig: DeliveryConfig,
    version: String,
    environment: Environment
  ): Boolean =
    evaluators.all { evaluator ->
      !environment.hasSupportedConstraint(evaluator) ||
        evaluator.canPromote(artifact, version, deliveryConfig, environment)
    }

  private fun Environment.hasSupportedConstraint(constraintEvaluator: ConstraintEvaluator<*>) =
    constraints.any { it.javaClass.isAssignableFrom(constraintEvaluator.supportedType.type) }
}
