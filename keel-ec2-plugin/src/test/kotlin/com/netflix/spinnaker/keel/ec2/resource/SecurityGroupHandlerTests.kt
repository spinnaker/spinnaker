/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.ec2.CidrRule
import com.netflix.spinnaker.keel.api.ec2.CrossAccountReferenceRule
import com.netflix.spinnaker.keel.api.ec2.PortRange
import com.netflix.spinnaker.keel.api.ec2.SecurityGroup
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupOverride
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.TCP
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.api.ec2.SelfReferenceRule
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupModel as ClouddriverSecurityGroup
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupModel.SecurityGroupRule
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupModel.SecurityGroupRuleCidr
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupModel.SecurityGroupRulePortRange
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupModel.SecurityGroupRuleReference
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.parseMoniker
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.plugin.OrcaTaskLauncher
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.keel.plugin.TaskLauncher
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import java.util.UUID.randomUUID
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull

internal class SecurityGroupHandlerTests : JUnit5Minutests {
  private val cloudDriverService: CloudDriverService = mockk()
  private val cloudDriverCache: CloudDriverCache = mockk()
  private val orcaService: OrcaService = mockk()
  private val deliveryConfigRepository: InMemoryDeliveryConfigRepository = mockk() {
    // we're just using this to get notifications
    every { environmentFor(any()) } returns Environment("test")
  }
  private val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
  private val taskLauncher = OrcaTaskLauncher(
    orcaService,
    deliveryConfigRepository,
    publisher
  )
  private val objectMapper = configuredObjectMapper()
  private val normalizers = emptyList<Resolver<SecurityGroupSpec>>()
  private val regions = listOf("us-west-3", "us-east-17")

  interface Fixture {
    val cloudDriverService: CloudDriverService
    val cloudDriverCache: CloudDriverCache
    val orcaService: OrcaService
    val taskLauncher: TaskLauncher
    val objectMapper: ObjectMapper
    val normalizers: List<Resolver<SecurityGroupSpec>>
    val vpcRegion1: Network
    val vpcRegion2: Network
    val handler: SecurityGroupHandler
    val securityGroupSpec: SecurityGroupSpec
    val securityGroupBase: SecurityGroup
    val regionalSecurityGroups: Map<String, SecurityGroup>
  }

  data class CurrentFixture(
    override val cloudDriverService: CloudDriverService,
    override val cloudDriverCache: CloudDriverCache,
    override val orcaService: OrcaService,
    override val taskLauncher: TaskLauncher,
    override val objectMapper: ObjectMapper,
    override val normalizers: List<Resolver<SecurityGroupSpec>>,
    override val vpcRegion1: Network =
      Network(CLOUD_PROVIDER, randomUUID().toString(), "vpc1", "prod", "us-west-3"),
    override val vpcRegion2: Network =
      Network(CLOUD_PROVIDER, randomUUID().toString(), "vpc1", "prod", "us-east-17"),
    override val handler: SecurityGroupHandler =
      SecurityGroupHandler(cloudDriverService, cloudDriverCache, orcaService, taskLauncher, normalizers),

    override val securityGroupSpec: SecurityGroupSpec =
      SecurityGroupSpec(
        moniker = Moniker(
          app = "keel",
          stack = "fnord"
        ),
        locations = SimpleLocations(
          account = vpcRegion1.account,
          vpc = vpcRegion1.name!!,
          regions = setOf(SimpleRegionSpec(vpcRegion1.region), SimpleRegionSpec(vpcRegion2.region))
        ),
        description = "dummy security group"
      ),
    override val securityGroupBase: SecurityGroup =
      SecurityGroup(
        moniker = Moniker(
          app = "keel",
          stack = "fnord"
        ),
        location = SecurityGroup.Location(
          account = vpcRegion1.account,
          vpc = vpcRegion1.name!!,
          region = "placeholder"
        ),
        description = "dummy security group"
      ),
    override val regionalSecurityGroups: Map<String, SecurityGroup> =
      mapOf(
        "us-west-3" to securityGroupBase.copy(
          location = SecurityGroup.Location(
            account = securityGroupBase.location.account,
            vpc = securityGroupBase.location.vpc,
            region = "us-west-3"
          )
        ),
        "us-east-17" to securityGroupBase.copy(
          location = SecurityGroup.Location(
            account = securityGroupBase.location.account,
            vpc = securityGroupBase.location.vpc,
            region = "us-east-17"
          )
        )
      ),
    val cloudDriverResponse1: ClouddriverSecurityGroup =
      ClouddriverSecurityGroup(
        CLOUD_PROVIDER,
        "sg-3a0c495f",
        "keel-fnord",
        "dummy security group",
        vpcRegion1.account,
        vpcRegion1.region,
        vpcRegion1.id,
        emptySet(),
        Moniker(app = "keel", stack = "fnord")
      ),
    val cloudDriverResponse2: ClouddriverSecurityGroup =
      ClouddriverSecurityGroup(
        CLOUD_PROVIDER,
        "sg-5a2a497d",
        "keel-fnord",
        "dummy security group",
        vpcRegion2.account,
        vpcRegion2.region,
        vpcRegion2.id,
        emptySet(),
        Moniker(app = "keel", stack = "fnord")
      ),
    val cloudDriverSummaryResponseWest: SecurityGroupSummary =
      SecurityGroupSummary("keel-fnord", "sg-3a0c495f", vpcRegion1.id),
    val cloudDriverSummaryResponseEast: SecurityGroupSummary =
      SecurityGroupSummary("keel-fnord", "sg-5a2a497d", vpcRegion2.id),
    val vpcOtherAccount: Network = Network(CLOUD_PROVIDER, randomUUID().toString(), "vpc0", "mgmt", vpcRegion1.region)
  ) : Fixture

  data class UpsertFixture(
    override val cloudDriverService: CloudDriverService,
    override val cloudDriverCache: CloudDriverCache,
    override val orcaService: OrcaService,
    override val taskLauncher: TaskLauncher,
    override val objectMapper: ObjectMapper,
    override val normalizers: List<Resolver<SecurityGroupSpec>>,
    override val vpcRegion1: Network =
      Network(CLOUD_PROVIDER, randomUUID().toString(), "vpc1", "prod", "us-west-3"),
    override val vpcRegion2: Network =
      Network(CLOUD_PROVIDER, randomUUID().toString(), "vpc1", "prod", "us-east-17"),
    override val handler: SecurityGroupHandler =
      SecurityGroupHandler(cloudDriverService, cloudDriverCache, orcaService, taskLauncher, normalizers),
    override val securityGroupSpec: SecurityGroupSpec =
      SecurityGroupSpec(
        moniker = Moniker(
          app = "keel",
          stack = "fnord"
        ),
        locations = SimpleLocations(
          account = vpcRegion1.account,
          vpc = vpcRegion1.name!!,
          regions = setOf(SimpleRegionSpec(vpcRegion1.region), SimpleRegionSpec(vpcRegion2.region))
        ),
        description = "dummy security group"
      ),
    override val securityGroupBase: SecurityGroup =
      SecurityGroup(
        moniker = Moniker(
          app = "keel",
          stack = "fnord"
        ),
        location = SecurityGroup.Location(
          account = vpcRegion1.account,
          vpc = vpcRegion1.name!!,
          region = "placeholder"
        ),
        description = "dummy security group"
      ),
    override val regionalSecurityGroups: Map<String, SecurityGroup> =
      mapOf(
        "us-west-3" to securityGroupBase.copy(
          location = SecurityGroup.Location(
            account = securityGroupBase.location.account,
            vpc = securityGroupBase.location.vpc,
            region = "us-west-3"
          )
        ),
        "us-east-17" to securityGroupBase.copy(
          location = SecurityGroup.Location(
            account = securityGroupBase.location.account,
            vpc = securityGroupBase.location.vpc,
            region = "us-east-17"
          )
        )
      )
  ) : Fixture

  fun currentTests() = rootContext<CurrentFixture> {
    fixture {
      CurrentFixture(cloudDriverService, cloudDriverCache, orcaService, taskLauncher, objectMapper, normalizers)
    }

    before {
      setupVpc()
    }

    context("no matching security group exists") {
      before { cloudDriverSecurityGroupNotFound() }

      test("current returns null") {
        val response = runBlocking {
          handler.current(resource)
        }

        expectThat(response).isEmpty()
      }
    }

    context("a matching security group exists") {
      before { cloudDriverSecurityGroupReturns() }

      test("current returns the security group") {
        val response = runBlocking {
          handler.current(resource)
        }
        expectThat(response)
          .hasSize(2)
        expectThat(response["us-west-3"])
          .isNotNull()
          .isEqualTo(regionalSecurityGroups["us-west-3"])
        expectThat(response["us-east-17"])
          .isNotNull()
          .isEqualTo(regionalSecurityGroups["us-east-17"])
      }

      test("export generates a spec for the existing security group") {
        val exportable = Exportable(
          cloudProvider = "aws",
          account = "prod",
          user = "fzlem@netflix.com",
          moniker = parseMoniker("keel-fnord"),
          regions = setOf("us-west-3", "us-east-17"),
          kind = handler.supportedKind.kind
        )
        val export = runBlocking {
          handler.export(exportable)
        }
        expectThat(export.locations.regions)
          .hasSize(2)
        expectThat(export.overrides)
          .hasSize(0)

        // The export cleanly diffs against the fixture spec
        val diff = DefaultResourceDiff(securityGroupSpec, export)
        expectThat(diff.hasChanges())
          .isFalse()
      }
    }

    context("a matching security group with ingress rules exists") {
      deriveFixture {
        copy(
          cloudDriverResponse1 = cloudDriverResponse1.copy(
            inboundRules = setOf(
              // cross account ingress rule from another app
              SecurityGroupRule(
                "tcp",
                listOf(SecurityGroupRulePortRange(443, 443)),
                SecurityGroupRuleReference("otherapp", vpcOtherAccount.account, vpcOtherAccount.region, vpcOtherAccount.id),
                null),
              // multi-port range self-referencing ingress
              SecurityGroupRule(
                "tcp",
                listOf(
                  SecurityGroupRulePortRange(7001, 7001),
                  SecurityGroupRulePortRange(7102, 7102)),
                SecurityGroupRuleReference(
                  cloudDriverResponse1.name,
                  cloudDriverResponse1.accountName,
                  cloudDriverResponse1.region,
                  vpcRegion1.id),
                null),
              // CIDR ingress
              SecurityGroupRule("tcp",
                listOf(
                  SecurityGroupRulePortRange(443, 443)),
                null,
                SecurityGroupRuleCidr("10.0.0.0", "/16"))
            )
          )
        )
      }

      before {
        with(vpcOtherAccount) {
          every { cloudDriverCache.networkBy(name, account, region) } returns this
          every { cloudDriverCache.networkBy(id) } returns this
        }

        cloudDriverSecurityGroupReturns()
      }

      test("rules are attached to the current security group") {
        val response = runBlocking {
          handler.current(resource)
        }
        expectThat(response)
          .isNotEmpty()
          .get { get(vpcRegion1.region)!!.inboundRules }
          .hasSize(cloudDriverResponse1.inboundRules.size + 1)
      }
    }
  }

  fun upsertTests() = rootContext<UpsertFixture> {
    fixture { UpsertFixture(cloudDriverService, cloudDriverCache, orcaService, taskLauncher, objectMapper, normalizers) }

    before {
      setupVpc()
    }

    sequenceOf(
      "create" to SecurityGroupHandler::create,
      "update" to SecurityGroupHandler::update
    )
      .forEach { (methodName, handlerMethod) ->
        context("$methodName a security group with no ingress rules") {
          before {
            clearMocks(orcaService)
            coEvery { orcaService.orchestrate("keel@spinnaker", any()) } answers {
              TaskRefResponse("/tasks/${randomUUID()}")
            }

            runBlocking {
              handlerMethod.invoke(
                handler,
                resource,
                DefaultResourceDiff(handler.desired(resource), null))
            }
          }

          after {
            confirmVerified(orcaService)
          }

          test("it upserts the security group via Orca") {
            val tasks = mutableListOf<OrchestrationRequest>()
            coVerify { orcaService.orchestrate("keel@spinnaker", capture(tasks)) }

            // Expect 2 tasks covering both regions in SecurityGroupSpec
            expectThat(tasks).hasSize(2)
            expectThat(tasks.flatMap { it.job.first()["regions"] as List<String> })
              .hasSize(2)
              .containsExactly(regions)

            tasks
              .forEach {
                expectThat(it) {
                  application.isEqualTo(securityGroupSpec.moniker.app)
                  job
                    .hasSize(1)
                    .first()
                    .type
                    .isEqualTo("upsertSecurityGroup")
                }
              }

            expectThat(tasks.map { it.trigger.correlationId }.toSet()).hasSize(2)
          }
        }

        context("$methodName a security group with a reference ingress rule") {
          deriveFixture {
            copy(
              securityGroupSpec = securityGroupSpec.copy(
                inboundRules = setOf(
                  CrossAccountReferenceRule(
                    protocol = TCP,
                    account = "test",
                    name = "otherapp",
                    vpc = "vpc1",
                    portRange = PortRange(startPort = 443, endPort = 443)
                  )
                )
              )
            )
          }

          before {
            securityGroupSpec.locations.regions.forEach { region ->
              Network(CLOUD_PROVIDER, randomUUID().toString(), "vpc1", "test", region.name)
                .also {
                  every { cloudDriverCache.networkBy(it.id) } returns it
                  every { cloudDriverCache.networkBy(it.name, it.account, it.region) } returns it
                }
            }

            clearMocks(orcaService)
            coEvery { orcaService.orchestrate("keel@spinnaker", any()) } answers {
              TaskRefResponse("/tasks/${randomUUID()}")
            }

            runBlocking {
              handlerMethod.invoke(
                handler,
                resource,
                DefaultResourceDiff(handler.desired(resource), null))
            }
          }

          after {
            confirmVerified(orcaService)
          }

          test("it upserts the security group via Orca") {
            val tasks = mutableListOf<OrchestrationRequest>()
            coVerify { orcaService.orchestrate("keel@spinnaker", capture(tasks)) }

            // task per region
            expectThat(tasks)
              .hasSize(2)
            expectThat(tasks.flatMap { it.job.first()["regions"] as List<String> })
              .hasSize(2)
              .containsExactly(regions)

            tasks.forEach { task ->
              expectThat(task) {
                application.isEqualTo(securityGroupSpec.moniker.app)
                job.hasSize(1)
                job[0].securityGroupIngress
                  .hasSize(1)
                  .first()
                  .and {
                    securityGroupSpec.inboundRules.first().also { rule ->
                      get("type").isEqualTo(rule.protocol.name.toLowerCase())
                      get("startPort").isEqualTo(rule.portRange.startPort)
                      get("endPort").isEqualTo(rule.portRange.endPort)
                      get("name").isEqualTo((rule as CrossAccountReferenceRule).name)
                    }
                  }
              }
            }
          }
        }

        context("$methodName a security group with an IP block range ingress rule") {
          deriveFixture {
            copy(
              securityGroupSpec = securityGroupSpec.copy(
                inboundRules = setOf(
                  CidrRule(
                    protocol = TCP,
                    blockRange = "10.0.0.0/16",
                    portRange = PortRange(startPort = 443, endPort = 443)
                  )
                )
              )
            )
          }

          before {
            clearMocks(orcaService)
            coEvery { orcaService.orchestrate("keel@spinnaker", any()) } answers {
              TaskRefResponse("/tasks/${randomUUID()}")
            }

            runBlocking {
              handlerMethod.invoke(
                handler,
                resource,
                DefaultResourceDiff(handler.desired(resource), null))
            }
          }

          after {
            confirmVerified(orcaService)
          }

          test("it upserts the security group via Orca") {
            val tasks = mutableListOf<OrchestrationRequest>()
            coVerify { orcaService.orchestrate("keel@spinnaker", capture(tasks)) }

            expectThat(tasks)
              .hasSize(2)
            expectThat(tasks.flatMap { it.job.first()["regions"] as List<String> })
              .hasSize(2)
              .containsExactly(regions)

            tasks.forEach { task ->
              expectThat(task) {
                application.isEqualTo(securityGroupSpec.moniker.app)
                job.hasSize(1)
                job[0].ipIngress
                  .hasSize(1)
                  .first()
                  .and {
                    securityGroupSpec.inboundRules.first().also { rule ->
                      get("type").isEqualTo(rule.protocol.name)
                      get("cidr").isEqualTo((rule as CidrRule).blockRange)
                      get("startPort").isEqualTo(rule.portRange.startPort)
                      get("endPort").isEqualTo(rule.portRange.endPort)
                    }
                  }
              }
            }
          }
        }
      }

    context("create a security group with a self-referential ingress rule") {
      deriveFixture {
        copy(
          securityGroupSpec = securityGroupSpec.copy(
            inboundRules = setOf(
              SelfReferenceRule(
                protocol = TCP,
                portRange = PortRange(startPort = 443, endPort = 443)
              )
            )
          )
        )
      }

      before {
        clearMocks(orcaService)
        coEvery { orcaService.orchestrate("keel@spinnaker", any()) } answers {
          TaskRefResponse("/tasks/${randomUUID()}")
        }

        runBlocking {
          handler.create(resource, DefaultResourceDiff(handler.desired(resource), null))
        }
      }

      after {
        confirmVerified(orcaService)
      }

      test("it does not try to create the self-referencing rule") {
        val tasks = mutableListOf<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(tasks)) }
        expectThat(tasks)
          .hasSize(2)
        expectThat(tasks.flatMap { it.job.first()["regions"] as List<String> })
          .hasSize(2)
          .containsExactly(regions)

        tasks.forEach { task ->
          expectThat(task) {
            application.isEqualTo(securityGroupSpec.moniker.app)
            job.hasSize(1)
            job.first().type.isEqualTo("upsertSecurityGroup")
            job.first().securityGroupIngress.hasSize(0)
          }
        }
      }
    }

    context("update a security group with a self-referential ingress rule") {
      deriveFixture {
        copy(
          securityGroupSpec = securityGroupSpec.copy(
            inboundRules = setOf(
              SelfReferenceRule(
                protocol = TCP,
                portRange = PortRange(startPort = 443, endPort = 443)
              )
            )
          )
        )
      }

      before {
        clearMocks(orcaService)
        coEvery { orcaService.orchestrate("keel@spinnaker", any()) } answers {
          TaskRefResponse("/tasks/${randomUUID()}")
        }

        runBlocking {
          val withoutIngress = resource
            .copy(
              spec = resource.spec.copy(
                inboundRules = emptySet()))

          handler.update(resource,
            DefaultResourceDiff(handler.desired(resource), handler.desired(withoutIngress)))
        }
      }

      after {
        confirmVerified(orcaService)
      }

      test("it includes self-referencing rule in the Orca task") {
        val tasks = mutableListOf<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(tasks)) }
        expectThat(tasks)
          .hasSize(2)
        expectThat(tasks.flatMap { it.job.first()["regions"] as List<String> })
          .hasSize(2)
          .containsExactly(regions)

        tasks.forEach { task ->
          expectThat(task) {
            application.isEqualTo(securityGroupSpec.moniker.app)
            job.hasSize(1)
            job.first().type.isEqualTo("upsertSecurityGroup")
            job.first().securityGroupIngress.hasSize(1)
          }
        }
      }
    }

    context("adding a region to an existing security group") {
      deriveFixture {
        copy(
          securityGroupSpec = securityGroupSpec.copy(
            inboundRules = setOf(
              CrossAccountReferenceRule(
                protocol = TCP,
                account = "test",
                name = "otherapp",
                vpc = "vpc1",
                portRange = PortRange(startPort = 443, endPort = 443)
              )
            )
          )
        )
      }

      before {
        clearMocks(orcaService)
        coEvery { orcaService.orchestrate("keel@spinnaker", any()) } answers {
          TaskRefResponse("/tasks/${randomUUID()}")
        }

        runBlocking {
          val onlyInEast = resource
            .copy(
              spec = resource.spec.copy(
                locations = SimpleLocations(
                  account = securityGroupSpec.locations.account,
                  vpc = securityGroupSpec.locations.vpc,
                  regions = setOf(SimpleRegionSpec("us-east-17"))
                )
              )
            )

          handler.update(
            resource,
            DefaultResourceDiff(handler.desired(resource), handler.desired(onlyInEast)))
        }
      }

      after {
        confirmVerified(orcaService)
      }

      test("it only creates a task for the missing region, us-west-3") {
        val tasks = mutableListOf<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(tasks)) }
        expectThat(tasks)
          .hasSize(1)
        expectThat(tasks[0].job)
          .hasSize(1)
        expectThat(tasks[0].job[0]["regions"] as List<String>)
          .hasSize(1)
          .containsExactly(listOf("us-west-3"))

        tasks.forEach { task ->
          expectThat(task) {
            application.isEqualTo(securityGroupSpec.moniker.app)
            job.hasSize(1)
            job.first().type.isEqualTo("upsertSecurityGroup")
            job.first().securityGroupIngress.hasSize(1)
          }
        }
      }
    }

    context("one region has an ingress override") {
      deriveFixture {
        copy(
          securityGroupSpec = securityGroupSpec.copy(
            overrides = mapOf(
              "us-east-17" to SecurityGroupOverride(
                inboundRules = setOf(
                  CidrRule(
                    protocol = TCP,
                    blockRange = "10.0.0.0/16",
                    portRange = PortRange(startPort = 443, endPort = 443)
                  )
                )
              )
            )
          )
        )
      }

      before {
        clearMocks(orcaService)
        coEvery { orcaService.orchestrate("keel@spinnaker", any()) } answers {
          TaskRefResponse("/tasks/${randomUUID()}")
        }

        runBlocking {
          val withoutOverride = resource
            .copy(
              spec = resource.spec.copy(
                overrides = emptyMap()))

          handler.upsert(
            resource,
            DefaultResourceDiff(handler.desired(resource), handler.desired(withoutOverride)))
        }
      }

      after {
        confirmVerified(orcaService)
      }

      test("it only creates a task for the newly overridden region") {
        val tasks = mutableListOf<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(tasks)) }
        expectThat(tasks)
          .hasSize(1)
        expectThat(tasks[0].job)
          .hasSize(1)
        expectThat(tasks[0].job[0]["regions"] as List<String>)
          .hasSize(1)
          .containsExactly(listOf("us-east-17"))
        expectThat(tasks[0].job[0]) {
          type.isEqualTo("upsertSecurityGroup")
          ipIngress.hasSize(1)
          ipIngress[0]["cidr"]
            .isEqualTo(
              (securityGroupSpec.overrides
                .getValue("us-east-17")
                .inboundRules!!.first() as CidrRule)
                .blockRange)
        }
      }
    }
  }

  private fun CurrentFixture.cloudDriverSecurityGroupReturns() {
    for (response in listOf(cloudDriverResponse1, cloudDriverResponse2)) {
      with(response) {
        coEvery {
          cloudDriverService.getSecurityGroup(any(), accountName, CLOUD_PROVIDER, name, region, vpcId)
        } returns this
      }
    }

    with(cloudDriverSummaryResponseWest) {
      coEvery {
        cloudDriverCache.securityGroupByName("prod", "us-west-3", name)
      } returns this
    }
    with(cloudDriverSummaryResponseEast) {
      coEvery {
        cloudDriverCache.securityGroupByName("prod", "us-east-17", name)
      } returns this
    }
  }

  private fun CurrentFixture.cloudDriverSecurityGroupNotFound() {
    for (response in listOf(cloudDriverResponse1, cloudDriverResponse2)) {
      with(response) {
        coEvery {
          cloudDriverService.getSecurityGroup(any(), accountName, CLOUD_PROVIDER, name, region, vpcId)
        } throws RETROFIT_NOT_FOUND
      }
    }
  }

  private fun <F : Fixture> F.setupVpc() {
    for (vpc in listOf(vpcRegion1, vpcRegion2)) {
      with(vpc) {
        every { cloudDriverCache.networkBy(name, account, region) } returns this
        every { cloudDriverCache.networkBy(id) } returns this
      }
    }
  }

  val Fixture.resource: Resource<SecurityGroupSpec>
    get() = resource(
      apiVersion = SPINNAKER_API_V1,
      kind = "ec2.SecurityGroup",
      spec = securityGroupSpec
    )
}

private val Assertion.Builder<OrchestrationRequest>.application: Assertion.Builder<String>
  get() = get(OrchestrationRequest::application)

private val Assertion.Builder<OrchestrationRequest>.job: Assertion.Builder<List<Job>>
  get() = get(OrchestrationRequest::job)

private val Assertion.Builder<Job>.type: Assertion.Builder<String>
  get() = get { getValue("type").toString() }

private val Assertion.Builder<Job>.securityGroupIngress: Assertion.Builder<List<Map<String, *>>>
  get() = get { getValue("securityGroupIngress") }.isA()

private val Assertion.Builder<Job>.ipIngress: Assertion.Builder<List<Map<String, *>>>
  get() = get { getValue("ipIngress") }.isA()
