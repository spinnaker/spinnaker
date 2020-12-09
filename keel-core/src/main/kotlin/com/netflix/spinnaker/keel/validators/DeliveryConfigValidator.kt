package com.netflix.spinnaker.keel.validators

import com.netflix.frigga.NameValidation
import com.netflix.spinnaker.keel.api.ArtifactReferenceProvider
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.id
import com.netflix.spinnaker.keel.exceptions.DuplicateArtifactReferenceException
import com.netflix.spinnaker.keel.exceptions.DuplicateResourceIdException
import com.netflix.spinnaker.keel.exceptions.InvalidAppNameException
import com.netflix.spinnaker.keel.exceptions.InvalidArtifactReferenceException
import com.netflix.spinnaker.keel.exceptions.MissingEnvironmentReferenceException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Provides delivery config validation functions
 */
@Component
class DeliveryConfigValidator {

  val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * Run validation checks against delivery config to ensure:
   *
   * - app name is valid
   * - resources have unique ids
   * - artifacts have unique references
   * - depends on environments are unique
   * - references to artifacts are valid
   *
   * Throws an exception if config fails any checks
   */
  fun validate(config: SubmittedDeliveryConfig) {

    /**
     * check: app name is valid Spinnaker app name
     */
    if(!NameValidation.checkName(config.application)) {
      log.warn("Validation failed for ${config.name}, invalid app name: ${config.application}")
      throw InvalidAppNameException(config.application)
    }

    /**
     * check: resources have unique ids
     */
    val resources = config.environments.map { it.resources }.flatten().map { it.id }
    val duplicateResources = resources.duplicates()

    if (duplicateResources.isNotEmpty()) {
      val envToResources: Map<String, MutableList<String>> = config.environments
        .map { env -> env.name to env.resources.map { it.spec.id }.toMutableList() }.toMap()
      val envsAndDuplicateResources = envToResources
        .filterValues { rs: MutableList<String> ->
          // remove all the resources we don't care about from this mapping
          rs.removeIf { it !in duplicateResources }
          // if there are resources left that we care about, leave it in the map
          rs.isNotEmpty()
        }
      log.warn("Validation failed for ${config.name}, duplicates resource ids found: $envsAndDuplicateResources")
      throw DuplicateResourceIdException(duplicateResources, envsAndDuplicateResources)
    }

    /**
     * check: artifacts have unique references
     */
    val refs = config.artifacts.map { it.reference }
    val duplicateRefs = refs.duplicates()

    if (duplicateRefs.isNotEmpty()) {
      val duplicatesArtifactNameToRef: Map<String, String> = config.artifacts
        .filter { duplicateRefs.contains(it.reference) }
        .associate { art -> art.name to art.reference }

      log.warn("Validation failed for ${config.name}, duplicate artifact references found: $duplicatesArtifactNameToRef")
      throw DuplicateArtifactReferenceException(duplicatesArtifactNameToRef)
    }

    /**
     * check: depends on environments uniqueness
     */
    config.environments.forEach { environment ->
      environment.constraints.forEach { constraint ->
        if (constraint is DependsOnConstraint) {
          config.environments.find {
            it.name == constraint.environment
          } ?: throw MissingEnvironmentReferenceException(constraint.environment)
        }
      }
    }

    /**
     * check: all referenced artifacts exist
     */
    config.environments.forEach { environment ->
      environment.resources.forEach { resource ->
        (resource.spec as? ArtifactReferenceProvider)
          ?.artifactReference
          ?.also {
            if (!refs.contains(it)) {
              throw InvalidArtifactReferenceException(it, refs)
            }
          }
      }
    }
  }

  /**
   * Return the duplicates in a list
   */
  private fun List<String>.duplicates(): List<String> =
    groupingBy { it }
      .eachCount()
      .filter { it.value > 1 }
      .keys
      .toList()
}
