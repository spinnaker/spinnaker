package com.netflix.spinnaker.keel.dgs

import com.netflix.graphql.dgs.DgsDataLoader
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.StatefulConstraint
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator
import com.netflix.spinnaker.keel.constraints.AllowedTimesConstraintAttributes
import com.netflix.spinnaker.keel.constraints.DependsOnConstraintAttributes
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.MANUAL_JUDGEMENT_CONSTRAINT_TYPE
import com.netflix.spinnaker.keel.core.api.TimeWindowConstraint
import com.netflix.spinnaker.keel.core.api.windowsNumeric
import com.netflix.spinnaker.keel.graphql.types.MdConstraint
import com.netflix.spinnaker.keel.graphql.types.MdConstraintStatus
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.services.removePrivateConstraintAttrs
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoaderWithContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Loads all constraint states for the given versions
 */
@DgsDataLoader(name = ConstraintsDataLoader.Descriptor.name)
class ConstraintsDataLoader(
  private val keelRepository: KeelRepository,
  constraintEvaluators: List<ConstraintEvaluator<*>>,
) : MappedBatchLoaderWithContext<EnvironmentArtifactAndVersion, List<MdConstraint>> {

  object Descriptor {
    const val name = "artifact-version-constraints"
  }

  private val statelessEvaluators: List<ConstraintEvaluator<*>> =
    constraintEvaluators.filter { !it.isImplicit() && it !is StatefulConstraintEvaluator<*, *> }

  // if key doesn't exist in persisted values, maybe it's all stateless or they haven't been evaluated
  private fun addMissingConstraints(
    requestedVersions: MutableSet<EnvironmentArtifactAndVersion>,
    constraintStates: MutableMap<EnvironmentArtifactAndVersion, MutableList<ConstraintState>>, config: DeliveryConfig
  ) {
    requestedVersions.forEach { key: EnvironmentArtifactAndVersion ->

      val environment = config.environments.firstOrNull { it.name == key.environmentName } ?: return@forEach
      val existingConstraints = constraintStates.getOrPut(key) { mutableListOf() }

      environment.constraints
        .filter { envConstraint ->
          // filter out constraints that already have a state
          // This is done by comparing the type as we don't have a unique identifier and could lead to bugs
          // if users multiple constraints with the same type for the same environment
          existingConstraints.none { existingConstraint -> existingConstraint.type == envConstraint.type }
        }
        .forEach { envConstraint ->
          // no summary for this constraint
          val newConstraint = if (envConstraint is StatefulConstraint) {
            var state = ConstraintState(
              deliveryConfigName = config.name,
              environmentName = key.environmentName,
              artifactVersion = key.version,
              artifactReference = key.artifactReference,
              type = envConstraint.type,
              status = ConstraintStatus.NOT_EVALUATED
            )
            if (envConstraint is TimeWindowConstraint) {
              // we need to load in allowed time attrs to display in the UI
              state = state.copy(
                attributes = AllowedTimesConstraintAttributes(
                  allowedTimes = envConstraint.windowsNumeric,
                  timezone = envConstraint.tz,
                  currentlyPassing = false
                )
              )
            }
            state
          } else { // Stateless constraint
            val evaluator = statelessEvaluators.find { evaluator ->
              evaluator.supportedType.name == envConstraint.type
            } ?: return@forEach // This should never happen, but we bail if we don't find an evaluator

            // Evaluate the current status of the constraint
            val artifact = config.matchingArtifactByReference(key.artifactReference) ?: return@forEach
            val passes = evaluator.canPromote(artifact, version = key.version, deliveryConfig = config, targetEnvironment = environment)

            ConstraintState(
              deliveryConfigName = config.name,
              environmentName = key.environmentName,
              artifactVersion = key.version,
              artifactReference = key.artifactReference,
              type = envConstraint.type,
              status = if (passes) ConstraintStatus.PASS else ConstraintStatus.PENDING,
              attributes = when (envConstraint) {
                is DependsOnConstraint -> DependsOnConstraintAttributes(envConstraint.environment, passes)
                else -> null
              }
            )
          }
          existingConstraints.add(newConstraint)
        }
    }
  }

  fun getConstraintsState(
    requestedVersions: MutableSet<EnvironmentArtifactAndVersion>,
    config: DeliveryConfig
  ): MutableMap<EnvironmentArtifactAndVersion, MutableList<ConstraintState>> {
    val persistedStates = keelRepository.constraintStateForEnvironments(config.name)
      .removePrivateConstraintAttrs() // remove attributes that should not be exposed
      .filter { constraintState ->
        // remove old state from any deleted constraints
        val existingConstraints = config.environments.find { it.name == constraintState.environmentName }?.constraints?.map { it.type } ?: emptyList()
        existingConstraints.contains(constraintState.type)
      }
      .groupByTo(mutableMapOf()) {
        EnvironmentArtifactAndVersion(environmentName = it.environmentName, artifactReference = it.artifactReference, version = it.artifactVersion)
      }
    addMissingConstraints(requestedVersions, persistedStates, config)
    return persistedStates
  }

  override fun load(
    keys: MutableSet<EnvironmentArtifactAndVersion>,
    environment: BatchLoaderEnvironment
  ): CompletionStage<MutableMap<EnvironmentArtifactAndVersion, List<MdConstraint>>> {
    val context: ApplicationContext = DgsContext.getCustomContext(environment)
    return CompletableFuture.supplyAsync {
      // TODO: optimize that by querying only the needed versions
      val config = context.getConfig()
      val constraintStates = getConstraintsState(keys, config)
      constraintStates.mapValues { pair -> pair.value.map { it.toDgs() } }.toMutableMap()
    }
  }
}

fun ConstraintState.toDgs() =
  MdConstraint(
    type = type,
    status = when (status) {
      ConstraintStatus.NOT_EVALUATED -> MdConstraintStatus.BLOCKED
      ConstraintStatus.PENDING -> MdConstraintStatus.PENDING
      ConstraintStatus.FAIL -> MdConstraintStatus.FAIL
      ConstraintStatus.PASS -> MdConstraintStatus.PASS
      ConstraintStatus.OVERRIDE_FAIL -> MdConstraintStatus.FAIL
      ConstraintStatus.OVERRIDE_PASS -> if (type == MANUAL_JUDGEMENT_CONSTRAINT_TYPE) {
        MdConstraintStatus.PASS
      } else {
        MdConstraintStatus.FORCE_PASS
      }
    },
    startedAt = createdAt,
    judgedAt = judgedAt,
    judgedBy = judgedBy,
    attributes = attributes,
    comment = comment
  )
