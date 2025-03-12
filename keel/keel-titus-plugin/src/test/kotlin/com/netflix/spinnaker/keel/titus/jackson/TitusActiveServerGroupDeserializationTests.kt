package com.netflix.spinnaker.keel.titus.jackson

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.clouddriver.model.CustomizedMetricSpecificationModel
import com.netflix.spinnaker.keel.clouddriver.model.TargetPolicyDescriptor
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.TitusScaling
import com.netflix.spinnaker.keel.jackson.KeelApiModule
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess

class TitusActiveServerGroupDeserializationTests {

  val mapper = configuredObjectMapper()
    .registerModule(KeelApiModule)
    .registerModule(KeelTitusApiModule)

  @Test
  fun `can deserialize a server group with a scaling policy`() {
    val expected = TitusScaling.Policy.TargetPolicy(
      targetPolicyDescriptor = TargetPolicyDescriptor(
        targetValue = 2.0,
        scaleOutCooldownSec = 300,
        scaleInCooldownSec = 300,
        disableScaleIn = false,
        customizedMetricSpecification = CustomizedMetricSpecificationModel(
          metricName = "AverageCPUUtilization",
          namespace = "NFLX/EPIC",
          statistic = "Average"
        )
      )
    )

    expectCatching {
      readResource<TitusActiveServerGroup>("/titus-active-server-group-with-scaling.json")
    }
      .isSuccess()
      .get { scalingPolicies }
      .hasSize(1)
      .first().get { policy } isEqualTo expected
  }

  @Test
  fun `can deserialize a server group with multiple scaling policies`() {
    expectCatching {
      readResource<TitusActiveServerGroup>("/titus-active-server-group-with-complex-scaling.json")
    }
      .isSuccess()
      .get { scalingPolicies }
      .hasSize(3)
  }

  private inline fun <reified T> readResource(path: String) =
    mapper.readValue<T>(resource(path))

  private fun resource(path: String) = checkNotNull(javaClass.getResource(path)) {
    "Resource $path not found"
  }
}
