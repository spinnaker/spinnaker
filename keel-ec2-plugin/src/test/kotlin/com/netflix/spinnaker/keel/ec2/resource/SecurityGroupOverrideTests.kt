package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.ec2.AllPorts
import com.netflix.spinnaker.keel.api.ec2.EC2_SECURITY_GROUP_V1
import com.netflix.spinnaker.keel.api.ec2.ReferenceRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupOverride
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.TCP
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.test.resource
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.hasSize
import strikt.assertions.isNotNull

class SecurityGroupOverrideTests {
  val cloudDriverService = mockk<CloudDriverService>()
  val cloudDriverCache = mockk<CloudDriverCache>()
  val orcaService = mockk<OrcaService>()
  val taskLauncher = mockk<TaskLauncher>()

  val securityGroupHandler = SecurityGroupHandler(
    cloudDriverService = cloudDriverService,
    cloudDriverCache = cloudDriverCache,
    orcaService = orcaService,
    taskLauncher = taskLauncher,
    resolvers = emptyList()
  )

  @Test
  fun `can merge ingress rules specified in overrides`() {
    val spec = SecurityGroupSpec(
      moniker = Moniker(
        app = "fnord"
      ),
      locations = SimpleLocations(
        account = "test",
        regions = setOf(
          SimpleRegionSpec(
            name = "uk-east-17"
          ),
          SimpleRegionSpec(
            name = "ap-south-1"
          )
        )
      ),
      description = "catflap rubberplant marzipan",
      inboundRules = setOf(
        ReferenceRule(
          protocol = TCP,
          name = "common",
          portRange = AllPorts
        )
      ),
      overrides = mapOf(
        "uk-east-17" to SecurityGroupOverride(
          inboundRules = setOf(
            ReferenceRule(
              protocol = TCP,
              name = "one-region-only",
              portRange = AllPorts
            )
          )
        )
      )
    )

    val materialized = runBlocking {
      securityGroupHandler.desired(resource(kind = EC2_SECURITY_GROUP_V1.kind, spec = spec))
    }

    expect {
      that(materialized["uk-east-17"]?.inboundRules).isNotNull().hasSize(2)
      that(materialized["ap-south-1"]?.inboundRules).isNotNull().hasSize(1)
    }
  }
}
