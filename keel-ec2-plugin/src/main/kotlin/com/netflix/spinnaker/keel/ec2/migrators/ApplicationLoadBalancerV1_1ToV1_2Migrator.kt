package com.netflix.spinnaker.keel.ec2.migrators

import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.ApplicationLoadBalancerOverride
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Listener
import com.netflix.spinnaker.keel.api.ec2.EC2_APPLICATION_LOAD_BALANCER_V1_1
import com.netflix.spinnaker.keel.api.ec2.EC2_APPLICATION_LOAD_BALANCER_V1_2
import com.netflix.spinnaker.keel.api.ec2.old.ApplicationLoadBalancerV1_1Spec
import com.netflix.spinnaker.keel.api.ec2.old.ApplicationLoadBalancerV1_1Spec.ListenerV1_1
import com.netflix.spinnaker.keel.resources.SpecMigrator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("keel.plugins.ec2.enabled")
class ApplicationLoadBalancerV1_1ToV1_2Migrator : SpecMigrator<ApplicationLoadBalancerV1_1Spec, ApplicationLoadBalancerSpec> {
  @Suppress("DEPRECATION")
  override val input = EC2_APPLICATION_LOAD_BALANCER_V1_1
  override val output = EC2_APPLICATION_LOAD_BALANCER_V1_2

  override fun migrate(spec: ApplicationLoadBalancerV1_1Spec): ApplicationLoadBalancerSpec =
    ApplicationLoadBalancerSpec(
      moniker = spec.moniker,
      locations = spec.locations,
      internal = spec.internal,
      dependencies = spec.dependencies,
      idleTimeout = spec.idleTimeout,
      listeners = spec.listeners.migrate(),
      targetGroups = spec.targetGroups,
      overrides = spec.overrides.mapValues { (_, v) ->
        ApplicationLoadBalancerOverride(
          v.dependencies,
          v.listeners?.migrate(),
          v.targetGroups
        )
      }
    )

  private fun Set<ListenerV1_1>.migrate(): Set<Listener> =
    mapTo(mutableSetOf()) { listener ->
      Listener(
        port = listener.port,
        protocol = listener.protocol,
        certificate = listener.certificateArn?.substringAfter(":server-certificate/"),
        rules = listener.rules,
        defaultActions = listener.defaultActions
      )
    }
}
