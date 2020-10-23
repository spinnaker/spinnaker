package com.netflix.spinnaker.keel.ec2.migrators

import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.TargetGroup
import com.netflix.spinnaker.keel.api.ec2.EC2_APPLICATION_LOAD_BALANCER_V1
import com.netflix.spinnaker.keel.api.ec2.EC2_APPLICATION_LOAD_BALANCER_V1_1
import com.netflix.spinnaker.keel.api.ec2.old.ApplicationLoadBalancerV1Spec
import com.netflix.spinnaker.keel.resources.SpecMigrator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("keel.plugins.ec2.enabled")
class ApplicationLoadBalancerV1ToV1_1Migrator : SpecMigrator<ApplicationLoadBalancerV1Spec, ApplicationLoadBalancerSpec> {
  @Suppress("DEPRECATION")
  override val input = EC2_APPLICATION_LOAD_BALANCER_V1
  override val output = EC2_APPLICATION_LOAD_BALANCER_V1_1

  override fun migrate(spec: ApplicationLoadBalancerV1Spec): ApplicationLoadBalancerSpec =
    ApplicationLoadBalancerSpec(
      moniker = spec.moniker,
      locations = spec.locations,
      internal = spec.internal,
      dependencies = spec.dependencies,
      idleTimeout = spec.idleTimeout,
      listeners = spec.listeners,
      targetGroups = spec.targetGroups.map {
        TargetGroup(
          name = it.name,
          targetType = it.targetType,
          protocol = it.protocol,
          port = it.port,
          healthCheckEnabled = it.healthCheckEnabled,
          healthCheckTimeout = it.healthCheckTimeoutSeconds,
          healthCheckPort = it.healthCheckPort,
          healthCheckProtocol = it.healthCheckProtocol,
          healthCheckHttpCode = it.healthCheckHttpCode,
          healthCheckPath = it.healthCheckPath,
          healthCheckInterval = it.healthCheckIntervalSeconds,
          healthyThresholdCount = it.healthyThresholdCount,
          unhealthyThresholdCount = it.unhealthyThresholdCount,
          attributes = it.attributes
        )
      }.toSet(),
      overrides = spec.overrides
    )
}
