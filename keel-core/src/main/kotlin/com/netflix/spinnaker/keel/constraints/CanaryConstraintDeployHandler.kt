package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.CanaryConstraint
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.events.Task

interface CanaryConstraintDeployHandler {
  val supportedClouds: Set<String>

  /**
   * Deploy and analyze a canary using cloud provider specific logic.
   *
   * @param constraint The canary constraint configuration
   * @param version Artifact to be canaried
   * @param deliveryConfig The [DeliveryConfig] containing the environment gated by this canary
   * @param targetEnvironment The [Environment] gated by this canary
   * @param regions Regions that need a canary deployment, may be a subset of regions defined in [constraint]
   *
   * @return A map where values are [Task]'s pointing to regional orca canary tasks, keyed by region
   */
  suspend fun deployCanary(
    constraint: CanaryConstraint,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    regions: Set<String>
  ): Map<String, Task>
}
