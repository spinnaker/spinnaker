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

import com.netflix.spinnaker.keel.api.Capacity
import com.netflix.spinnaker.keel.api.ClusterDependencies
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.Highlander
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.RedBlack
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.TagVersionStrategy.INCREASING_TAG
import com.netflix.spinnaker.keel.api.TagVersionStrategy.SEMVER_JOB_COMMIT_BY_SEMVER
import com.netflix.spinnaker.keel.api.TagVersionStrategy.SEMVER_TAG
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.api.titus.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.api.titus.SPINNAKER_TITUS_API_V1
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
import com.netflix.spinnaker.keel.clouddriver.model.DockerImage
import com.netflix.spinnaker.keel.clouddriver.model.Placement
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.ServiceJobProcesses
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroupImage
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.docker.DigestProvider
import com.netflix.spinnaker.keel.docker.VersionedTagProvider
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.parseMoniker
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.plugin.OrcaTaskLauncher
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
import java.time.Clock
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.context.ApplicationEventPublisher
import retrofit2.HttpException
import retrofit2.Response
import strikt.api.Assertion
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.containsKey
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNull
import strikt.assertions.isTrue
import strikt.assertions.map

// todo eb: we could probably have generic cluster tests
// where you provide the correct info for the spec and active server groups
class TitusClusterHandlerTests : JUnit5Minutests {
  val cloudDriverService = mockk<CloudDriverService>()
  val cloudDriverCache = mockk<CloudDriverCache>()
  val orcaService = mockk<OrcaService>()
  val resolvers = emptyList<Resolver<TitusClusterSpec>>()
  val deliveryConfigRepository: InMemoryDeliveryConfigRepository = mockk()
  val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
  val taskLauncher = OrcaTaskLauncher(
    orcaService,
    deliveryConfigRepository,
    publisher
  )
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
    _defaults = TitusServerGroupSpec(
      container = DigestProvider(
        organization = "spinnaker",
        image = "keel",
        digest = "sha:1111"
      ),
      capacity = Capacity(1, 6, 4),
      dependencies = ClusterDependencies(
        loadBalancerNames = setOf("keel-test-frontend"),
        securityGroupNames = setOf(sg1West.name)
      )
    )
  )

  val serverGroups = spec.resolve()
  val serverGroupEast = serverGroups.first { it.location.region == "us-east-1" }
  val serverGroupWest = serverGroups.first { it.location.region == "us-west-2" }

  val activeServerGroupResponseEast = serverGroupEast.toClouddriverResponse(listOf(sg1East, sg2East))
  val activeServerGroupResponseWest = serverGroupWest.toClouddriverResponse(listOf(sg1West, sg2West))

  val resource = resource(
    apiVersion = SPINNAKER_TITUS_API_V1,
    kind = "cluster",
    spec = spec
  )

  val exportable = Exportable(
    cloudProvider = "titus",
    account = spec.locations.account,
    user = "fzlem@netflix.com",
    moniker = spec.moniker,
    regions = spec.locations.regions.map { it.name }.toSet(),
    kind = "cluster"
  )

  val images = listOf(
    DockerImage(
      account = "testregistry",
      repository = "emburns/spin-titus-demo",
      tag = "1",
      digest = "sha:2222"
    ),
    DockerImage(
      account = "testregistry",
      repository = "emburns/spin-titus-demo",
      tag = "2",
      digest = "sha:3333"
    )
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
        taskLauncher,
        publisher,
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
      coEvery { orcaService.orchestrate(resource.serviceAccount, any()) } returns TaskRefResponse("/tasks/${UUID.randomUUID()}")
      every { deliveryConfigRepository.environmentFor(any()) } returns Environment("test")
    }

    after {
      confirmVerified(orcaService)
      clearAllMocks()
    }

    context("the cluster does not exist or has no active server groups") {
      before {
        coEvery { cloudDriverService.titusActiveServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
        coEvery { cloudDriverService.titusActiveServerGroup(any(), "us-west-2") } throws RETROFIT_NOT_FOUND
        coEvery { cloudDriverService.findDockerImages("testregistry", "spinnaker/keel", any(), any()) } returns
          listOf(DockerImage("testregistry", "spinnaker/keel", "master-h2.blah", "sha:1111"))
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

      test("resolving diff a diff creates a new server group") {
        runBlocking {
          upsert(resource, DefaultResourceDiff(serverGroups.byRegion(), emptyMap()))
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

        expectThat(slot.captured.job.first()) {
          get("type").isEqualTo("createServerGroup")
        }
      }
    }

    context("the cluster has active server groups") {
      before {
        coEvery { cloudDriverService.titusActiveServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
        coEvery { cloudDriverService.titusActiveServerGroup(any(), "us-west-2") } returns activeServerGroupResponseWest
        coEvery { cloudDriverService.findDockerImages("testregistry", "spinnaker/keel", any(), any()) } returns
          listOf(DockerImage("testregistry", "spinnaker/keel", "master-h2.blah", "sha:1111"))
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
        val diff = DefaultResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("resolving diff resizes the current server group") {
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

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
        val diff = DefaultResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("resolving diff clones the current server group") {
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

          expectThat(slot.captured.job.first()) {
            get("type").isEqualTo("createServerGroup")
            get("source").isEqualTo(
              mapOf(
                "account" to activeServerGroupResponseWest.placement.account,
                "region" to activeServerGroupResponseWest.region,
                "asgName" to activeServerGroupResponseWest.name
              )
            )
          }
        }

        test("the default deploy strategy is used") {
          val deployWith = RedBlack()
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

          expectThat(slot.captured.job.first()) {
            get("strategy").isEqualTo("redblack")
            get("delayBeforeDisableSec").isEqualTo(deployWith.delayBeforeDisable.seconds)
            get("delayBeforeScaleDownSec").isEqualTo(deployWith.delayBeforeScaleDown.seconds)
            get("rollback").isA<Map<String, Any?>>().get("onFailure").isEqualTo(deployWith.rollbackOnFailure)
            get("scaleDown").isEqualTo(deployWith.resizePreviousToZero)
            get("maxRemainingAsgs").isEqualTo(deployWith.maxServerGroups)
          }
        }

        test("the deploy strategy is configured") {
          val deployWith = RedBlack(
            resizePreviousToZero = true,
            delayBeforeDisable = Duration.ofMinutes(1),
            delayBeforeScaleDown = Duration.ofMinutes(5),
            maxServerGroups = 3
          )
          runBlocking {
            upsert(resource.copy(spec = resource.spec.copy(deployWith = deployWith)), diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

          expectThat(slot.captured.job.first()) {
            get("strategy").isEqualTo("redblack")
            get("delayBeforeDisableSec").isEqualTo(deployWith.delayBeforeDisable.seconds)
            get("delayBeforeScaleDownSec").isEqualTo(deployWith.delayBeforeScaleDown.seconds)
            get("rollback").isA<Map<String, Any?>>().get("onFailure").isEqualTo(deployWith.rollbackOnFailure)
            get("scaleDown").isEqualTo(deployWith.resizePreviousToZero)
            get("maxRemainingAsgs").isEqualTo(deployWith.maxServerGroups)
          }
        }

        test("a different deploy strategy is used") {
          runBlocking {
            upsert(resource.copy(spec = resource.spec.copy(deployWith = Highlander)), diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

          expectThat(slot.captured.job.first()) {
            get("strategy").isEqualTo("highlander")
            not().containsKey("delayBeforeDisableSec")
            not().containsKey("delayBeforeScaleDownSec")
            not().containsKey("rollback")
            not().containsKey("scaleDown")
            not().containsKey("maxRemainingAsgs")
          }
        }
      }

      context("multiple server groups have a diff") {

        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name).withDifferentRuntimeOptions(),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withDoubleCapacity()
        )
        val diff = DefaultResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        before {
          runBlocking {
            upsert(resource, diff)
          }
        }

        test("resolving diff launches one task per server group") {
          val tasks = mutableListOf<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(any(), capture(tasks)) }

          expectThat(tasks)
            .hasSize(2)
            .map { it.job.first()["type"] }
            .containsExactlyInAnyOrder("createServerGroup", "resizeServerGroup")
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
      context("export without overrides") {
        before {
          coEvery { cloudDriverService.titusActiveServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
          coEvery { cloudDriverService.titusActiveServerGroup(any(), "us-west-2") } returns activeServerGroupResponseWest
          coEvery { cloudDriverService.findDockerImages("testregistry", (spec.defaults.container!! as DigestProvider).repository()) } returns images
          coEvery { cloudDriverService.getAccountInformation(titusAccount) } returns mapOf("registry" to "testregistry")
        }

        derivedContext<TitusClusterSpec>("exported titus cluster spec") {
          deriveFixture {
            runBlocking {
              export(exportable)
            }
          }

          test("has the expected basic properties") {
            expectThat(locations.regions)
              .hasSize(2)
            expectThat(overrides)
              .hasSize(0)
          }
        }

        test("has default values in defaults omitted") {
          expectThat(spec.defaults.constraints)
            .isNull()
          expectThat(spec.defaults.entryPoint)
            .isNull()
          expectThat(spec.defaults.migrationPolicy)
            .isNull()
          expectThat(spec.defaults.resources)
            .isNull()
          expectThat(spec.defaults.iamProfile)
            .isNull()
          expectThat(spec.defaults.capacityGroup)
            .isNull()
          expectThat(spec.defaults.env)
            .isNull()
          expectThat(spec.defaults.containerAttributes)
            .isNull()
          expectThat(spec.defaults.tags)
            .isNull()
        }
      }

      context("export with overrides") {
        before {
          coEvery { cloudDriverService.titusActiveServerGroup(any(), "us-east-1") } returns
            activeServerGroupResponseEast
          coEvery { cloudDriverService.titusActiveServerGroup(any(), "us-west-2") } returns
            activeServerGroupResponseWest
              .withDifferentEntryPoint()
              .withDifferentEnv()
              .withDoubleCapacity()
          coEvery { cloudDriverService.findDockerImages("testregistry", (spec.defaults.container!! as DigestProvider).repository()) } returns images
          coEvery { cloudDriverService.getAccountInformation(titusAccount) } returns mapOf("registry" to "testregistry")
        }

        derivedContext<TitusClusterSpec>("exported titus cluster spec") {
          deriveFixture {
            runBlocking {
              export(exportable)
            }
          }

          test("has overrides matching differences in the server groups") {
            val overrideDiff = DefaultResourceDiff(overrides["us-west-2"]!!, defaults)
            val addedOrChangedProps = overrideDiff.children
              .filter { it.isAdded || it.isChanged }
              .map { it.propertyName }
              .toSet()
            expectThat(locations.regions)
              .hasSize(2)
            expectThat(overrides)
              .hasSize(1)
            expectThat(overrides)
              .containsKey("us-west-2")
            expectThat(overrideDiff.hasChanges())
              .isTrue()
            expectThat(addedOrChangedProps)
              .isEqualTo(setOf("entryPoint", "capacity", "env"))
          }

          test("has default values in overrides omitted") {
            val override = overrides["us-west-2"]!!
            expectThat(override) {
              get { constraints }.isNull()
              get { migrationPolicy }.isNull()
              get { resources }.isNull()
              get { iamProfile }.isNull()
              get { capacityGroup }.isNull()
              get { containerAttributes }.isNull()
              get { tags }.isNull()
            }
          }
        }
      }
    }

    context("figuring out tagging strategy") {
      val image = DockerImage(
        account = "testregistry",
        repository = "emburns/spin-titus-demo",
        tag = "12",
        digest = "sha:1111"
      )
      test("number") {
        expectThat(findTagVersioningStrategy(image)).isEqualTo(INCREASING_TAG)
      }
      test("semver with v") {
        expectThat(findTagVersioningStrategy(image.copy(tag = "v1.12.3-rc.1"))).isEqualTo(SEMVER_TAG)
      }
      test("semver without v") {
        expectThat(findTagVersioningStrategy(image.copy(tag = "1.12.3-rc.1"))).isEqualTo(SEMVER_TAG)
      }
      test("branch-job-commit") {
        expectThat(findTagVersioningStrategy(image.copy(tag = "master-h3.2317144"))).isEqualTo(BRANCH_JOB_COMMIT_BY_JOB)
      }
      test("semver-job-commit parses to semver version") {
        expectThat(findTagVersioningStrategy(image.copy(tag = "v1.12.3-rc.1-h1196.49b8dc5"))).isEqualTo(SEMVER_JOB_COMMIT_BY_SEMVER)
      }
    }

    context("generate container") {
      val container = DigestProvider(
        organization = "emburns",
        image = "spin-titus-demo",
        digest = "sha:1111"
      )

      before {
        coEvery { cloudDriverService.findDockerImages("testregistry", container.repository()) } returns images
        coEvery { cloudDriverService.getAccountInformation(titusAccount) } returns mapOf("registry" to "testregistry")
      }

      test("no sha match does not generate an artifact strategy") {
        expectThat(generateContainer(container, titusAccount)).isEqualTo(container)
      }

      test("sha match generates a container with a strategy") {
        val generatedContainer = generateContainer(
          container = container.copy(digest = "sha:2222"),
          account = titusAccount)
        expect {
          that(generatedContainer).isA<VersionedTagProvider>().get { tagVersionStrategy }.isEqualTo(INCREASING_TAG)
        }
      }
    }
  }

  private suspend fun CloudDriverService.titusActiveServerGroup(user: String, region: String) = titusActiveServerGroup(
    user = user,
    app = spec.moniker.app,
    account = spec.locations.account,
    cluster = spec.moniker.toString(),
    region = region,
    cloudProvider = CLOUD_PROVIDER
  )
}

private fun TitusServerGroup.withDoubleCapacity(): TitusServerGroup =
  copy(
    capacity = Capacity(
      min = capacity.min * 2,
      max = capacity.max * 2,
      desired = capacity.desired!! * 2
    )
  )

private fun TitusServerGroup.withDifferentRuntimeOptions(): TitusServerGroup =
  copy(capacityGroup = "aDifferentGroup")

private fun TitusActiveServerGroup.withDoubleCapacity(): TitusActiveServerGroup =
  copy(
    capacity = Capacity(
      min = capacity.min * 2,
      max = capacity.max * 2,
      desired = capacity.desired!! * 2
    )
  )

private fun TitusActiveServerGroup.withDifferentEnv(): TitusActiveServerGroup =
  copy(env = mapOf("foo" to "bar"))

private fun TitusActiveServerGroup.withDifferentEntryPoint(): TitusActiveServerGroup =
  copy(entryPoint = "/bin/blah")

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
