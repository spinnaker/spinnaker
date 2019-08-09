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
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.securityGroup.CidrRule
import com.netflix.spinnaker.keel.api.ec2.securityGroup.CrossAccountReferenceRule
import com.netflix.spinnaker.keel.api.ec2.securityGroup.PortRange
import com.netflix.spinnaker.keel.api.ec2.securityGroup.SecurityGroup
import com.netflix.spinnaker.keel.api.ec2.securityGroup.SecurityGroupRule.Protocol.TCP
import com.netflix.spinnaker.keel.api.ec2.securityGroup.SelfReferenceRule
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup.SecurityGroupRule
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup.SecurityGroupRuleCidr
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup.SecurityGroupRulePortRange
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup.SecurityGroupRuleReference
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.util.UUID.randomUUID
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup as ClouddriverSecurityGroup

internal class SecurityGroupHandlerTests : JUnit5Minutests {
  private val cloudDriverService: CloudDriverService = mockk()
  private val cloudDriverCache: CloudDriverCache = mockk()
  private val orcaService: OrcaService = mockk()
  private val objectMapper = configuredObjectMapper()
  private val normalizers = emptyList<ResourceNormalizer<SecurityGroup>>()

  interface Fixture {
    val cloudDriverService: CloudDriverService
    val cloudDriverCache: CloudDriverCache
    val orcaService: OrcaService
    val objectMapper: ObjectMapper
    val normalizers: List<ResourceNormalizer<SecurityGroup>>
    val vpc: Network
    val handler: SecurityGroupHandler
    val securityGroup: SecurityGroup
  }

  data class CurrentFixture(
    override val cloudDriverService: CloudDriverService,
    override val cloudDriverCache: CloudDriverCache,
    override val orcaService: OrcaService,
    override val objectMapper: ObjectMapper,
    override val normalizers: List<ResourceNormalizer<SecurityGroup>>,
    override val vpc: Network =
      Network(CLOUD_PROVIDER, randomUUID().toString(), "vpc1", "prod", "us-west-3"),
    override val handler: SecurityGroupHandler =
      SecurityGroupHandler(cloudDriverService, cloudDriverCache, orcaService, objectMapper, normalizers),
    override val securityGroup: SecurityGroup =
      SecurityGroup(
        moniker = Moniker(
          app = "keel",
          stack = "fnord"
        ),
        accountName = vpc.account,
        region = vpc.region,
        vpcName = vpc.name,
        description = "dummy security group"
      ),
    val cloudDriverResponse: ClouddriverSecurityGroup =
      ClouddriverSecurityGroup(
        CLOUD_PROVIDER,
        "sg-3a0c495f",
        "keel-fnord",
        "dummy security group",
        vpc.account,
        vpc.region,
        vpc.id,
        emptySet(),
        Moniker(app = "keel", stack = "fnord")
      ),
    val vpc2: Network = Network(CLOUD_PROVIDER, randomUUID().toString(), "vpc0", "mgmt", vpc.region)
  ) : Fixture

  data class UpsertFixture(
    override val cloudDriverService: CloudDriverService,
    override val cloudDriverCache: CloudDriverCache,
    override val orcaService: OrcaService,
    override val objectMapper: ObjectMapper,
    override val normalizers: List<ResourceNormalizer<SecurityGroup>>,
    override val vpc: Network =
      Network(CLOUD_PROVIDER, randomUUID().toString(), "vpc1", "prod", "us-west-3"),
    override val handler: SecurityGroupHandler =
      SecurityGroupHandler(cloudDriverService, cloudDriverCache, orcaService, objectMapper, normalizers),
    override val securityGroup: SecurityGroup =
      SecurityGroup(
        moniker = Moniker(
          app = "keel",
          stack = "fnord"
        ),
        accountName = vpc.account,
        region = vpc.region,
        vpcName = vpc.name,
        description = "dummy security group"
      )
  ) : Fixture

  fun currentTests() = rootContext<CurrentFixture> {
    fixture {
      CurrentFixture(cloudDriverService, cloudDriverCache, orcaService, objectMapper, normalizers)
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

        expectThat(response).isNull()
      }
    }

    context("a matching security group exists") {
      before { cloudDriverSecurityGroupReturns() }

      test("current returns the security group") {
        val response = runBlocking {
          handler.current(resource)
        }
        expectThat(response)
          .isNotNull()
          .isEqualTo(securityGroup)
      }
    }

    context("a matching security group with ingress rules exists") {
      deriveFixture {
        copy(
          cloudDriverResponse = cloudDriverResponse.copy(
            inboundRules = setOf(
              // cross account ingress rule from another app
              SecurityGroupRule("tcp", listOf(SecurityGroupRulePortRange(443, 443)), SecurityGroupRuleReference("otherapp", vpc2.account, vpc2.region, vpc2.id), null),
              // multi-port range self-referencing ingress
              SecurityGroupRule("tcp", listOf(SecurityGroupRulePortRange(7001, 7001), SecurityGroupRulePortRange(7102, 7102)), SecurityGroupRuleReference(cloudDriverResponse.name, cloudDriverResponse.accountName, cloudDriverResponse.region, vpc.id), null),
              // CIDR ingress
              SecurityGroupRule("tcp", listOf(SecurityGroupRulePortRange(443, 443)), null, SecurityGroupRuleCidr("10.0.0.0", "/16"))
            )
          )
        )
      }

      before {
        with(vpc2) {
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
          .isNotNull()
          .get { inboundRules }
          .hasSize(cloudDriverResponse.inboundRules.size + 1)
      }
    }
  }

  fun upsertTests() = rootContext<UpsertFixture> {
    fixture { UpsertFixture(cloudDriverService, cloudDriverCache, orcaService, objectMapper, normalizers) }

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
            coEvery { orcaService.orchestrate("keel@spinnaker", any()) } answers {
              TaskRefResponse("/tasks/${randomUUID()}")
            }

            runBlocking {
              handlerMethod.invoke(handler, resource, ResourceDiff(resource.spec, null))
            }
          }

          after {
            confirmVerified(orcaService)
          }

          test("it upserts the security group via Orca") {
            val slot = slot<OrchestrationRequest>()
            coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }
            expectThat(slot.captured) {
              application.isEqualTo(securityGroup.moniker.app)
              job
                .hasSize(1)
                .first()
                .type
                .isEqualTo("upsertSecurityGroup")
            }
          }
        }

        context("$methodName a security group with a reference ingress rule") {
          deriveFixture {
            copy(
              securityGroup = securityGroup.copy(
                inboundRules = setOf(
                  CrossAccountReferenceRule(
                    protocol = TCP,
                    account = "test",
                    name = "otherapp",
                    vpcName = "vpc1",
                    portRange = PortRange(startPort = 443, endPort = 443)
                  )
                )
              )
            )
          }

          before {
            Network(CLOUD_PROVIDER, randomUUID().toString(), "vpc1", "test", securityGroup.region).also {
              every { cloudDriverCache.networkBy(it.id) } returns it
              every { cloudDriverCache.networkBy(it.name, it.account, it.region) } returns it
            }

            coEvery { orcaService.orchestrate("keel@spinnaker", any()) } answers {
              TaskRefResponse("/tasks/${randomUUID()}")
            }

            runBlocking {
              handlerMethod.invoke(handler, resource, ResourceDiff(resource.spec, null))
            }
          }

          after {
            confirmVerified(orcaService)
          }

          test("it upserts the security group via Orca") {
            val slot = slot<OrchestrationRequest>()
            coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }
            expectThat(slot.captured) {
              application.isEqualTo(securityGroup.moniker.app)
              job.hasSize(1)
              job[0].securityGroupIngress
                .hasSize(1)
                .first()
                .and {
                  securityGroup.inboundRules.first().also { rule ->
                    get("type").isEqualTo(rule.protocol.name.toLowerCase())
                    get("startPort").isEqualTo(rule.portRange.startPort)
                    get("endPort").isEqualTo(rule.portRange.endPort)
                    get("name").isEqualTo((rule as CrossAccountReferenceRule).name)
                  }
                }
            }
          }
        }

        context("$methodName a security group with an IP block range ingress rule") {
          deriveFixture {
            copy(
              securityGroup = securityGroup.copy(
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
            coEvery { orcaService.orchestrate("keel@spinnaker", any()) } answers {
              TaskRefResponse("/tasks/${randomUUID()}")
            }

            runBlocking {
              handlerMethod.invoke(handler, resource, ResourceDiff(resource.spec, null))
            }
          }

          after {
            confirmVerified(orcaService)
          }

          test("it upserts the security group via Orca") {
            val slot = slot<OrchestrationRequest>()
            coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }
            expectThat(slot.captured) {
              application.isEqualTo(securityGroup.moniker.app)
              job.hasSize(1)
              job[0].ipIngress
                .hasSize(1)
                .first()
                .and {
                  securityGroup.inboundRules.first().also { rule ->
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

    context("create a security group with a self-referential ingress rule") {
      deriveFixture {
        copy(
          securityGroup = securityGroup.copy(
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
        coEvery { orcaService.orchestrate("keel@spinnaker", any()) } answers {
          TaskRefResponse("/tasks/${randomUUID()}")
        }

        runBlocking {
          handler.create(resource, ResourceDiff(resource.spec, null))
        }
      }

      after {
        confirmVerified(orcaService)
      }

      test("it does not try to create the self-referencing rule") {
        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }
        expectThat(slot.captured) {
          application.isEqualTo(securityGroup.moniker.app)
          job.hasSize(1)
          job.first().type.isEqualTo("upsertSecurityGroup")
          job.first().securityGroupIngress.hasSize(0)
        }
      }
    }

    context("update a security group with a self-referential ingress rule") {
      deriveFixture {
        copy(
          securityGroup = securityGroup.copy(
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
        coEvery { orcaService.orchestrate("keel@spinnaker", any()) } answers {
          TaskRefResponse("/tasks/${randomUUID()}")
        }

        runBlocking {
          handler.update(resource, ResourceDiff(resource.spec, null))
        }
      }

      after {
        confirmVerified(orcaService)
      }

      test("it includes self-referencing rule in the Orca task") {
        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }
        expectThat(slot.captured) {
          application.isEqualTo(securityGroup.moniker.app)
          job.hasSize(1)
          job.first().type.isEqualTo("upsertSecurityGroup")
          job.first().securityGroupIngress.hasSize(1)
        }
      }
    }
  }

  fun deleteTests() = rootContext<UpsertFixture> {
    fixture { UpsertFixture(cloudDriverService, cloudDriverCache, orcaService, objectMapper, normalizers) }

    context("deleting a security group") {
      before {
        coEvery { orcaService.orchestrate("keel@spinnaker", any()) } answers {
          TaskRefResponse("/tasks/${randomUUID()}")
        }

        runBlocking {
          handler.delete(resource)
        }
      }

      after {
        confirmVerified(orcaService)
      }

      test("it deletes the security group via Orca") {
        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }
        expectThat(slot.captured) {
          application.isEqualTo(securityGroup.moniker.app)
          job
            .hasSize(1)
            .first()
            .type
            .isEqualTo("deleteSecurityGroup")
        }
      }
    }
  }

  private fun CurrentFixture.cloudDriverSecurityGroupReturns() {
    with(cloudDriverResponse) {
      coEvery {
        cloudDriverService.getSecurityGroup(any(), accountName, CLOUD_PROVIDER, name, region, vpcId)
      } returns this
    }
  }

  private fun CurrentFixture.cloudDriverSecurityGroupNotFound() {
    with(cloudDriverResponse) {
      coEvery {
        cloudDriverService.getSecurityGroup(any(), accountName, CLOUD_PROVIDER, name, region, vpcId)
      } throws RETROFIT_NOT_FOUND
    }
  }

  private fun <F : Fixture> F.setupVpc() {
    with(vpc) {
      every { cloudDriverCache.networkBy(name, account, region) } returns this
      every { cloudDriverCache.networkBy(id) } returns this
    }
  }

  val Fixture.resource: Resource<SecurityGroup>
    get() = Resource(
      apiVersion = SPINNAKER_API_V1,
      metadata = mapOf(
        "name" to with(securityGroup) {
          "ec2.SecurityGroup:${moniker.app}:$accountName:$region:${moniker.name}"
        },
        "uid" to randomUID(),
        "serviceAccount" to "keel@spinnaker",
        "application" to securityGroup.moniker.app
      ),
      kind = "ec2.SecurityGroup",
      spec = securityGroup
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
