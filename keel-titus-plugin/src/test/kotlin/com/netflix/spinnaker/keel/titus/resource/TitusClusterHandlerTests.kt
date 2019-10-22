/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.keel.titus.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.keel.api.Capacity
import com.netflix.spinnaker.keel.api.ClusterDependencies
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.titus.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.api.titus.cluster.Container
import com.netflix.spinnaker.keel.api.titus.cluster.TitusClusterHandler
import com.netflix.spinnaker.keel.api.titus.cluster.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.cluster.TitusServerGroup
import com.netflix.spinnaker.keel.api.titus.cluster.TitusServerGroupSpec
import com.netflix.spinnaker.keel.api.titus.cluster.byRegion
import com.netflix.spinnaker.keel.api.titus.cluster.moniker
import com.netflix.spinnaker.keel.api.titus.cluster.resolve
import com.netflix.spinnaker.keel.api.titus.cluster.resolveCapacity
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Placement
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.ServiceJobProcesses
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroupImage
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.parseMoniker
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.context.ApplicationEventPublisher
import retrofit2.HttpException
import retrofit2.Response
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.containsKey
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.map
import java.time.Clock
import java.util.UUID

// todo eb: we could probably have generic cluster tests
// where you provide the correct info for the spec and active server groups
class TitusClusterHandlerTests : JUnit5Minutests {
  val cloudDriverService = mockk<CloudDriverService>()
  val cloudDriverCache = mockk<CloudDriverCache>()
  val orcaService = mockk<OrcaService>()
  val objectMapper = ObjectMapper().registerKotlinModule()
  val resolvers = emptyList<Resolver<TitusClusterSpec>>()
  val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
  val clock = Clock.systemDefaultZone()

  val sg1West = SecurityGroupSummary("keel", "sg-325234532", "vpc-1")
  val sg2West = SecurityGroupSummary("keel-elb", "sg-235425234", "vpc-1")
  val sg1East = SecurityGroupSummary("keel", "sg-279585936", "vpc-1")
  val sg2East = SecurityGroupSummary("keel-elb", "sg-610264122", "vpc-1")

  val titusAccount = "titustest"
  val awsAccount = "test"

  val spec = TitusClusterSpec(
    moniker = Moniker(app = "keel", stack = "test"),
    locations = SimpleLocations(
      account = titusAccount,
      regions = setOf(SimpleRegionSpec("us-east-1"), SimpleRegionSpec("us-west-2"))
    ),
    container = Container(
      organization = "spinnaker",
      image = "keel",
      digest = "sha:1111"
    ),
    _defaults = TitusServerGroupSpec(
      capacity = Capacity(1, 6, 4),
      dependencies = ClusterDependencies(
        loadBalancerNames = setOf("keel-test-frontend"),
        securityGroupNames = setOf(sg1West.name)
      ),
      container = Container(
        organization = "spinnaker",
        image = "keel",
        digest = "sha:1111"
      )
    )
  )

  val serverGroups = spec.resolve()
  val serverGroupEast = serverGroups.first { it.location.region == "us-east-1" }
  val serverGroupWest = serverGroups.first { it.location.region == "us-west-2" }

  val activeServerGroupResponseEast = serverGroupEast.toClouddriverResponse(listOf(sg1East, sg2East))
  val activeServerGroupResponseWest = serverGroupWest.toClouddriverResponse(listOf(sg1West, sg2West))

  val resource = resource(
    apiVersion = SPINNAKER_API_V1,
    kind = "titus-cluster",
    spec = spec
  )

  private fun TitusServerGroup.toClouddriverResponse(
    securityGroups: List<SecurityGroupSummary>
  ): TitusActiveServerGroup =
    RandomStringUtils.randomNumeric(3).padStart(3, '0').let { sequence ->
      TitusActiveServerGroup(
        name = "$name-v$sequence",
        awsAccount = awsAccount,
        placement = Placement(location.account, location.region, emptyList()),
        region = location.region,
        image = TitusActiveServerGroupImage("${container.organization}/${container.image}", "", container.digest),
        iamProfile = moniker.app + "InstanceProfile",
        entryPoint = entryPoint,
        targetGroups = dependencies.targetGroups,
        loadBalancers = dependencies.loadBalancerNames,
        securityGroups = securityGroups.map(SecurityGroupSummary::id).toSet(),
        capacity = capacity,
        cloudProvider = CLOUD_PROVIDER,
        moniker = parseMoniker("$name-v$sequence"),
        env = env,
        constraints = constraints,
        migrationPolicy = migrationPolicy,
        serviceJobProcesses = ServiceJobProcesses(),
        tags = emptyMap(),
        resources = resources,
        capacityGroup = moniker.app
      )
    }

  fun tests() = rootContext<TitusClusterHandler> {
    fixture {
      TitusClusterHandler(
        cloudDriverService,
        cloudDriverCache,
        orcaService,
        clock,
        publisher,
        objectMapper,
        resolvers
      )
    }

    before {
      with(cloudDriverCache) {
        every { securityGroupById(awsAccount, "us-west-2", sg1West.id) } returns sg1West
        every { securityGroupById(awsAccount, "us-west-2", sg2West.id) } returns sg2West
        every { securityGroupByName(awsAccount, "us-west-2", sg1West.name) } returns sg1West
        every { securityGroupByName(awsAccount, "us-west-2", sg2West.name) } returns sg2West

        every { securityGroupById(awsAccount, "us-east-1", sg1East.id) } returns sg1East
        every { securityGroupById(awsAccount, "us-east-1", sg2East.id) } returns sg2East
        every { securityGroupByName(awsAccount, "us-east-1", sg1East.name) } returns sg1East
        every { securityGroupByName(awsAccount, "us-east-1", sg2East.name) } returns sg2East

        coEvery { cloudDriverService.getAccountInformation(titusAccount) } returns mapOf(
          "awsAccount" to awsAccount,
          "registry" to awsAccount + "registry"
        )
      }
      coEvery { orcaService.orchestrate("keel@spinnaker", any()) } returns TaskRefResponse("/tasks/${UUID.randomUUID()}")
    }

    after {
      confirmVerified(orcaService)
      clearAllMocks()
    }

    context("the cluster does not exist or has no active server groups") {
      before {
        coEvery { cloudDriverService.titusActiveServerGroup("us-east-1") } returns activeServerGroupResponseEast
        coEvery { cloudDriverService.titusActiveServerGroup("us-west-2") } throws RETROFIT_NOT_FOUND
      }

      test("the current model is null") {
        val current = runBlocking {
          current(resource)
        }
        expectThat(current)
          .hasSize(1)
          .not()
          .containsKey("us-west-2")
      }

      test("annealing a diff creates a new server group") {
        runBlocking {
          upsert(resource, ResourceDiff(serverGroups.byRegion(), emptyMap()))
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }

        expectThat(slot.captured.job.first()) {
          get("type").isEqualTo("createServerGroup")
        }
      }
    }

    context("the cluster has active server groups") {
      before {
        coEvery { cloudDriverService.titusActiveServerGroup("us-east-1") } returns activeServerGroupResponseEast
        coEvery { cloudDriverService.titusActiveServerGroup("us-west-2") } returns activeServerGroupResponseWest
      }

      // TODO: test for multiple server group response
      derivedContext<Map<String, TitusServerGroup>>("fetching the current server group state") {
        deriveFixture {
          runBlocking {
            current(resource)
          }
        }

        test("the current model is converted to a set of server group") {
          expectThat(this).isNotEmpty()
        }

        test("the server group name is derived correctly") {
          expectThat(values)
            .map { it.name }
            .containsExactlyInAnyOrder(
              activeServerGroupResponseEast.name,
              activeServerGroupResponseWest.name
            )
        }
      }
    }

    context("a diff has been detected") {
      context("the diff is only in capacity") {

        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withDoubleCapacity()
        )
        val diff = ResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("annealing resizes the current server group") {
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }

          expectThat(slot.captured.job.first()) {
            get("type").isEqualTo("resizeServerGroup")
            get("capacity").isEqualTo(
              spec.resolveCapacity("us-west-2").let {
                mapOf(
                  "min" to it.min,
                  "max" to it.max,
                  "desired" to it.desired
                )
              }
            )
            get("serverGroupName").isEqualTo(activeServerGroupResponseWest.name)
          }
        }
      }

      context("the diff is something other than just capacity") {

        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withDoubleCapacity().withDifferentRuntimeOptions()
        )
        val diff = ResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("annealing clones the current server group") {
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }

          expectThat(slot.captured.job.first()) {
            get("type").isEqualTo("upsertServerGroup")
            get("source").isEqualTo(
              mapOf(
                "account" to activeServerGroupResponseWest.placement.account,
                "region" to activeServerGroupResponseWest.region,
                "asgName" to activeServerGroupResponseWest.name
              )
            )
          }
        }
      }

      context("multiple server groups have a diff") {

        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name).withDifferentRuntimeOptions(),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withDoubleCapacity()
        )
        val diff = ResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        before {
          runBlocking {
            upsert(resource, diff)
          }
        }

        test("annealing launches one task per server group") {
          val tasks = mutableListOf<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(any(), capture(tasks)) }

          expectThat(tasks)
            .hasSize(2)
            .map { it.job.first()["type"] }
            .containsExactlyInAnyOrder("upsertServerGroup", "resizeServerGroup")
        }

        test("each task has a distinct correlation id") {
          val tasks = mutableListOf<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(any(), capture(tasks)) }

          expectThat(tasks)
            .hasSize(2)
            .map { it.trigger.correlationId }
            .containsDistinctElements()
        }
      }
    }
  }

  private suspend fun CloudDriverService.titusActiveServerGroup(region: String) = titusActiveServerGroup(
    serviceAccount = "keel@spinnaker",
    app = spec.moniker.app,
    account = spec.locations.account,
    cluster = spec.moniker.name,
    region = region,
    cloudProvider = CLOUD_PROVIDER
  )
}

private fun TitusServerGroup.withDoubleCapacity(): TitusServerGroup =
  copy(
    capacity = Capacity(
      min = capacity.min * 2,
      max = capacity.max * 2,
      desired = capacity.desired * 2
    )
  )

private fun TitusServerGroup.withDifferentRuntimeOptions(): TitusServerGroup =
  copy(capacityGroup = "aDifferentGroup")

private fun <E, T : Iterable<E>> Assertion.Builder<T>.containsDistinctElements() =
  assert("contains distinct elements") { subject ->
    val duplicates = subject
      .associateWith { elem -> subject.count { it == elem } }
      .filterValues { it > 1 }
      .keys
    when (duplicates.size) {
      0 -> pass()
      1 -> fail(duplicates.first(), "The element %s occurs more than once")
      else -> fail(duplicates, "The elements %s occur more than once")
    }
  }

val RETROFIT_NOT_FOUND = HttpException(
  Response.error<Any>(404, ResponseBody.create(MediaType.parse("application/json"), ""))
)
