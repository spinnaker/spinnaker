package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.constraints.ConstraintState
import com.netflix.spinnaker.keel.core.api.UID
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigName
import com.netflix.spinnaker.keel.persistence.OrphanedResourceException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH

class InMemoryDeliveryConfigRepository(
  private val clock: Clock = Clock.systemDefaultZone()
) : DeliveryConfigRepository {

  private val configs = mutableMapOf<String, DeliveryConfig>()
  private val constraints = mutableMapOf<String, ConstraintState>()
  private val queuedForApproval = mutableMapOf<String, MutableSet<String>>()
  private val applicationConstraintMapper = mutableMapOf<String, MutableSet<String>>()
  private val lastCheckTimes = mutableMapOf<String, Instant>()

  fun dropAll() {
    configs.clear()
    constraints.clear()
    applicationConstraintMapper.clear()
    lastCheckTimes.clear()
  }

  override fun getByApplication(application: String) =
    configs.values.filter { it.application == application }

  override fun deleteByApplication(application: String): Int {
    val size = configs.count { it.value.application == application }

    configs
      .values
      .filter { it.application == application }
      .map { it.application }
      .singleOrNull()
      ?.also {
        configs.remove(it)
        lastCheckTimes.remove(it)
      }

    return size
  }

  override fun get(name: String): DeliveryConfig =
    configs[name] ?: throw NoSuchDeliveryConfigName(name)

  override fun store(deliveryConfig: DeliveryConfig) {
    with(deliveryConfig) {
      configs[name] = this
      lastCheckTimes[name] = EPOCH
    }
  }

  override fun delete(name: String) {
    configs.remove(name)
  }

  override fun deleteResourceFromEnv(deliveryConfigName: String, environmentName: String, resourceId: String) {
    val config = get(deliveryConfigName)
    val newResources = config.environments
      .find { it.name == environmentName }
      ?.resources?.filter { it.id != resourceId }?.toSet() ?: emptySet()
    val envs = config.environments.map { env ->
      if (env.name == environmentName) {
        env.copy(resources = newResources)
      } else {
        env
      }
    }.toSet()
    val newConfig = config.copy(environments = envs)
    configs[deliveryConfigName] = newConfig
  }

  override fun deleteEnvironment(deliveryConfigName: String, environmentName: String) {
    val config = get(deliveryConfigName)
    configs[deliveryConfigName] = config.copy(
      environments = config.environments.filter { it.name != environmentName }.toSet()
    )
  }

  override fun environmentFor(resourceId: String): Environment =
    configs
      .values
      .flatMap { it.environments }
      .firstOrNull { it.resourceIds.contains(resourceId) }
      ?: throw OrphanedResourceException(resourceId)

  override fun deliveryConfigFor(resourceId: String): DeliveryConfig =
    configs
      .values
      .firstOrNull { it.resourceIds.contains(resourceId) }
      ?: throw OrphanedResourceException(resourceId)

  override fun storeConstraintState(state: ConstraintState) {
    val config = get(state.deliveryConfigName)
    val idPrefix = "${state.deliveryConfigName}:${state.environmentName}:${state.artifactVersion}:"
    val stateId = idPrefix + state.type

    constraints[stateId] = state
    applicationConstraintMapper
      .getOrPut(config.application, { mutableSetOf() })
      .add(stateId)

    val allPass = constraints
      .filterKeys { it.startsWith(idPrefix) }
      .values
      .all { it.status.passes() }

    if (allPass) {
      queueAllConstraintsApproved(state.deliveryConfigName, state.environmentName, state.artifactVersion)
    }
  }

  override fun constraintStateFor(application: String): List<ConstraintState> {
    return applicationConstraintMapper[application]
      ?.map {
        constraints[it] ?: error("Missing constraint state for $application ($it)")
      } ?: emptyList()
  }

  override fun getConstraintState(
    deliveryConfigName: String,
    environmentName: String,
    artifactVersion: String,
    type: String
  ): ConstraintState? =
    constraints["$deliveryConfigName:$environmentName:$artifactVersion:$type"]

  override fun constraintStateFor(
    deliveryConfigName: String,
    environmentName: String,
    artifactVersion: String
  ): List<ConstraintState> =
    constraints
      .filterKeys {
        it.startsWith("$deliveryConfigName:$environmentName:$artifactVersion:")
      }
      .map { it.value }

  override fun pendingConstraintVersionsFor(deliveryConfigName: String, environmentName: String): List<String> =
    constraints
      .filterKeys {
        it.startsWith("$deliveryConfigName:$environmentName:")
      }
      .map { it.value }
      .map { it.artifactVersion }

  override fun getQueuedConstraintApprovals(deliveryConfigName: String, environmentName: String): Set<String> =
    queuedForApproval.getOrDefault("$deliveryConfigName:$environmentName", emptySet())

  override fun queueAllConstraintsApproved(
    deliveryConfigName: String,
    environmentName: String,
    artifactVersion: String
  ) {
    val key = "$deliveryConfigName:$environmentName"
    queuedForApproval.getOrPut(key, ::mutableSetOf).add(artifactVersion)
  }

  override fun deleteQueuedConstraintApproval(
    deliveryConfigName: String,
    environmentName: String,
    artifactVersion: String
  ) {
    val key = "$deliveryConfigName:$environmentName"
    queuedForApproval.getOrPut(key, ::mutableSetOf).remove(artifactVersion)
  }

  override fun getConstraintStateById(uid: UID) =
    constraints[uid.toString()]

  override fun deleteConstraintState(
    deliveryConfigName: String,
    environmentName: String,
    type: String
  ) {
    val config = get(deliveryConfigName)
    val keys = constraints.keys.filter {
      it.startsWith("$deliveryConfigName:$environmentName:") &&
        it.endsWith(":$type")
    }
    keys.forEach {
      constraints.remove(it)
      applicationConstraintMapper
        .getOrPut(config.application, ::mutableSetOf)
        .remove(it)
    }
  }

  override fun constraintStateFor(
    deliveryConfigName: String,
    environmentName: String,
    limit: Int
  ): List<ConstraintState> =
    constraints
      .filter { it.key.startsWith("$deliveryConfigName:$environmentName:") }
      .map { it.value }
      .sortedByDescending { it.createdAt }
      .take(limit)

  override fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<DeliveryConfig> {
    val cutoff = clock.instant().minus(minTimeSinceLastCheck)
    return lastCheckTimes
      .filter { it.value <= cutoff }
      .keys
      .take(limit)
      .also { names ->
        names.forEach {
          lastCheckTimes[it] = clock.instant()
        }
      }
      .map { name -> configs[name] ?: error("No delivery config named $name") }
  }

  private val Environment.resourceIds: Iterable<String>
    get() = resources.map { it.id }

  private val DeliveryConfig.resourceIds: Iterable<String>
    get() = resources.map { it.id }
}
