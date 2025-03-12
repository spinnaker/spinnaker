package com.netflix.spinnaker.keel.titus.resource

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.CapacitySpec
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup
import com.netflix.spinnaker.keel.api.titus.TitusServerGroupSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.docker.DigestProvider
import com.netflix.spinnaker.keel.orca.ClusterExportHelper
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.titus.NETFLIX_CONTAINER_ENV_VARS
import com.netflix.spinnaker.keel.api.titus.TITUS_CLUSTER_V1
import com.netflix.spinnaker.keel.titus.TitusClusterHandler
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.contains
import strikt.assertions.getValue
import strikt.assertions.isEqualTo
import java.time.Clock

class TitusClusterDesiredStateResolutionTests : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    mapOf(
      TitusServerGroupSpec::containerAttributes to TitusServerGroup::containerAttributes,
      TitusServerGroupSpec::tags to TitusServerGroup::tags
    )
      .forEach { (specProperty, desiredProperty) ->
        context("the `$specProperty` property") {
          test("default value applies in non-overridden region") {
            expectThat(desired)
              .getValue("ca-central-1")
              .get(desiredProperty)
              .isEqualTo(specProperty.get(spec.defaults))
          }

          test("override value applies in overridden region") {
            expectThat(desired)
              .getValue("af-south-1")
              .get(desiredProperty)
              .isEqualTo(specProperty.get(spec.overrides.getValue("af-south-1")))
          }
        }
      }

    context("the `env` property") {
      test("default value applies in non-overridden region") {
        expectThat(desired["ca-central-1"]!!.env.entries)
          .contains(spec.defaults.env!!.entries)
      }

      test("override value applies in overridden region") {
        expectThat(desired["af-south-1"]!!.env.entries)
          .contains(spec.overrides["af-south-1"]!!.env!!.entries)
      }

      test("automatically includes Netflix default env vars in all regions") {
        desired.forEach { (region, serverGroup) ->
          expectThat(serverGroup.env.entries)
            .contains(NETFLIX_CONTAINER_ENV_VARS.associateWith { region }.entries)
        }
      }
    }

    context("the `capacity` property") {
      test("default value applies in non-overridden region") {
        expectThat(desired)
          .getValue("ca-central-1")
          .get(TitusServerGroup::capacity)
          .isEqualTo(spec.defaults.capacity?.run { Capacity.DefaultCapacity(min, max, desired!!)})
      }

      test("override value applies in overridden region") {
        expectThat(desired)
          .getValue("af-south-1")
          .get(TitusServerGroup::capacity)
          .isEqualTo(spec.overrides.getValue("af-south-1").capacity?.run { Capacity.DefaultCapacity(min, max, desired!!)})
      }
    }
  }

  class Fixture(
    val spec: TitusClusterSpec = TitusClusterSpec(
      moniker = Moniker(app = "fnord"),
      locations = SimpleLocations(
        "test",
        regions = setOf(
          SimpleRegionSpec("ca-central-1"),
          SimpleRegionSpec("af-south-1")
        )
      ),
      _defaults = TitusServerGroupSpec(
        capacity = CapacitySpec(1, 1, 1),
        containerAttributes = mapOf(
          "key" to "default value"
        ),
        env = mapOf(
          "key" to "default value"
        ),
        tags = mapOf(
          "key" to "default value"
        )
      ),
      container = DigestProvider(
        organization = "fnord",
        image = "some-image",
        digest = "4ef5d72110943e43fc029b63cf84939f64893401b0870eed34b66d5b72bead2c"
      ),
      overrides = mapOf(
        "af-south-1" to TitusServerGroupSpec(
          capacity = CapacitySpec(2, 2, 2),
          containerAttributes = mapOf(
            "key" to "override value"
          ),
          env = mapOf(
            "key" to "override value"
          ),
          tags = mapOf(
            "key" to "override value"
          )
        )
      )
    )
  ) {
    val resource = resource(
      kind = TITUS_CLUSTER_V1.kind,
      spec = spec
    )

    val cloudDriverService = mockk<CloudDriverService>()
    val cloudDriverCache = mockk<CloudDriverCache>()
    val orcaService = mockk<OrcaService>()
    val taskLauncher = mockk<TaskLauncher>()
    val publisher = mockk<EventPublisher>()
    val handler = TitusClusterHandler(
      cloudDriverService,
      cloudDriverCache,
      orcaService,
      Clock.systemDefaultZone(),
      taskLauncher,
      publisher,
      emptyList(),
      ClusterExportHelper(cloudDriverService, orcaService)
    )

    val desired: Map<String, TitusServerGroup> by lazy {
      runBlocking { handler.desired(resource) }
    }
  }
}
