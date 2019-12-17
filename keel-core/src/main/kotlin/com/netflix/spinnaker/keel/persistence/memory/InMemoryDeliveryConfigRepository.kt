package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.resources
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigName
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH

class InMemoryDeliveryConfigRepository(
  private val clock: Clock = Clock.systemDefaultZone()
) : DeliveryConfigRepository {

  private val configs = mutableMapOf<String, DeliveryConfig>()
  private val constraints = mutableMapOf<String, ConstraintState>()
  private val applicationConstraintMapper = mutableMapOf<String, MutableSet<String>>()
  private val lastCheckTimes = mutableMapOf<String, Instant>()
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

  override fun environmentFor(resourceId: ResourceId): Environment? =
    configs
      .values
      .flatMap { it.environments }
      .firstOrNull { it.resourceIds.contains(resourceId) }

  override fun deliveryConfigFor(resourceId: ResourceId): DeliveryConfig? =
    configs
      .values
      .firstOrNull { it.resourceIds.contains(resourceId) }

  override fun storeConstraintState(state: ConstraintState) {
    val config = get(state.deliveryConfigName)
    val stateId = "${state.deliveryConfigName}:${state.environmentName}:${state.artifactVersion}:" +
      state.type

    constraints[stateId] = state
    applicationConstraintMapper
      .getOrPut(config.application, { mutableSetOf() })
      .add(stateId)
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

  override fun hasDeliveryConfig(application: String): Boolean {
    return configs.values.any { it.application == application }
  }

  fun dropAll() {
    configs.clear()
    constraints.clear()
  }

  private val Environment.resourceIds: Iterable<ResourceId>
    get() = resources.map { it.id }

  private val DeliveryConfig.resourceIds: Iterable<ResourceId>
    get() = resources.map { it.id }
}
