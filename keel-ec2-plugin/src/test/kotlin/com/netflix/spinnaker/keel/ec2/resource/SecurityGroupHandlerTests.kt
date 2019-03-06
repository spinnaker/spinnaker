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

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.CidrRule
import com.netflix.spinnaker.keel.api.ec2.CrossAccountReferenceRule
import com.netflix.spinnaker.keel.api.ec2.PortRange
import com.netflix.spinnaker.keel.api.ec2.SecurityGroup
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.TCP
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup.SecurityGroupRule
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup.SecurityGroupRuleCidr
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup.SecurityGroupRulePortRange
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup.SecurityGroupRuleReference
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import kotlinx.coroutines.CompletableDeferred
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

internal object SecurityGroupHandlerTests : JUnit5Minutests {

  val cloudDriverService: CloudDriverService = mock()
  val cloudDriverCache: CloudDriverCache = mock()
  val orcaService: OrcaService = mock()
  val objectMapper = configuredObjectMapper()

  interface Fixture {
    val vpc: Network
    val handler: SecurityGroupHandler
    val securityGroup: SecurityGroup
  }

  data class CurrentFixture(
    override val vpc: Network =
      Network(CLOUD_PROVIDER, randomUUID().toString(), "vpc1", "prod", "us-west-3"),
    override val handler: SecurityGroupHandler =
      SecurityGroupHandler(cloudDriverService, cloudDriverCache, orcaService, objectMapper),
    override val securityGroup: SecurityGroup =
      SecurityGroup(
        application = "keel",
        name = "fnord",
        accountName = vpc.account,
        region = vpc.region,
        vpcName = vpc.name,
        description = "dummy security group"
      ),
    val cloudDriverResponse: com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup =
      com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup(
        CLOUD_PROVIDER,
        "sg-3a0c495f",
        "fnord",
        "dummy security group",
        vpc.account,
        vpc.region,
        vpc.id,
        emptySet(),
        Moniker("keel")
      ),
    val vpc2: Network = Network(CLOUD_PROVIDER, randomUUID().toString(), "vpc0", "mgmt", vpc.region)
  ) : Fixture

  data class UpsertFixture(
    override val vpc: Network =
      Network(CLOUD_PROVIDER, randomUUID().toString(), "vpc1", "prod", "us-west-3"),
    override val handler: SecurityGroupHandler =
      SecurityGroupHandler(cloudDriverService, cloudDriverCache, orcaService, objectMapper),
    override val securityGroup: SecurityGroup =
      SecurityGroup(
        application = "keel",
        name = "fnord",
        accountName = vpc.account,
        region = vpc.region,
        vpcName = vpc.name,
        description = "dummy security group"
      )
  ) : Fixture

  fun currentTests() = rootContext<CurrentFixture> {
    fixture {
      CurrentFixture()
    }

    before {
      setupVpc()
    }

    after {
      resetVpc()
    }

    context("no matching security group exists") {
      before { cloudDriverSecurityGroupNotFound() }
      after { resetServices() }

      test("current returns null") {
        val response = handler.current(resource)

        expectThat(response).isNull()
      }
    }

    context("a matching security group exists") {
      before { cloudDriverSecurityGroupReturns() }
      after { resetServices() }

      test("current returns the security group") {
        val response = handler.current(resource)
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
          whenever(cloudDriverCache.networkBy(name, account, region)) doReturn this
          whenever(cloudDriverCache.networkBy(id)) doReturn this
        }

        cloudDriverSecurityGroupReturns()
      }


      after {
        resetServices()
      }

      test("rules are attached to the current security group") {
        val response = handler.current(resource)
        expectThat(response)
          .isNotNull()
          .get { inboundRules }
          .hasSize(cloudDriverResponse.inboundRules.size + 1)
      }
    }
  }

  fun upsertTests() = rootContext<UpsertFixture> {
    fixture { UpsertFixture() }

    before {
      setupVpc()
    }

    after {
      resetVpc()
    }

    context("a security group with no ingress rules") {
      before {
        whenever(orcaService.orchestrate(any())) doAnswer {
          CompletableDeferred(TaskRefResponse("/tasks/${randomUUID()}"))
        }

        handler.upsert(resource)
      }

      after {
        reset(cloudDriverService, orcaService)
      }

      test("it upserts the security group via Orca") {
        argumentCaptor<OrchestrationRequest>().apply {
          verify(orcaService).orchestrate(capture())
          expectThat(firstValue) {
            application.isEqualTo(securityGroup.application)
            job
              .hasSize(1)
              .first()
              .type
              .isEqualTo("upsertSecurityGroup")
          }
        }
      }
    }

    context("a security group with a reference ingress rule") {
      deriveFixture {
        copy(
          securityGroup = securityGroup.copy(
            inboundRules = setOf(
              CrossAccountReferenceRule(
                protocol = TCP,
                account = "test",
                name = "otherapp",
                vpcName = "vpc1",
                portRange = PortRange(
                  startPort = 443,
                  endPort = 443
                )
              )
            )
          )
        )
      }

      before {
        Network(CLOUD_PROVIDER, randomUUID().toString(), "vpc1", "test", securityGroup.region).also {
          whenever(cloudDriverCache.networkBy(it.id)) doReturn it
          whenever(cloudDriverCache.networkBy(it.name, it.account, it.region)) doReturn it
        }

        whenever(orcaService.orchestrate(any())) doAnswer {
          CompletableDeferred(TaskRefResponse("/tasks/${randomUUID()}"))
        }

        handler.upsert(resource)
      }

      after {
        reset(cloudDriverService, orcaService)
      }

      test("it upserts the security group via Orca") {
        argumentCaptor<OrchestrationRequest>().apply {
          verify(orcaService).orchestrate(capture())
          expectThat(firstValue) {
            application.isEqualTo(securityGroup.application)
            job.hasSize(1)
            job[0]["securityGroupIngress"].isA<List<*>>()
              .hasSize(1).first().isA<Map<String, *>>()
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
    }

    context("a security group with an IP block range ingress rule") {
      deriveFixture {
        copy(
          securityGroup = securityGroup.copy(
            inboundRules = setOf(
              CidrRule(
                protocol = TCP,
                blockRange = "10.0.0.0/16",
                portRange = PortRange(
                  startPort = 443,
                  endPort = 443
                )
              )
            )
          )
        )
      }

      before {
        whenever(orcaService.orchestrate(any())) doAnswer {
          CompletableDeferred(TaskRefResponse("/tasks/${randomUUID()}"))
        }

        handler.upsert(resource)
      }

      after {
        reset(cloudDriverService, orcaService)
      }

      test("it upserts the security group via Orca") {
        argumentCaptor<OrchestrationRequest>().apply {
          verify(orcaService).orchestrate(capture())
          expectThat(firstValue) {
            application.isEqualTo(securityGroup.application)
            job.hasSize(1)
            job[0]["ipIngress"].isA<List<*>>()
              .hasSize(1).first().isA<Map<String, *>>()
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

    fun deleteTests() = rootContext<UpsertFixture> {
      fixture { UpsertFixture() }

      context("deleting a security group") {
        before {
          whenever(orcaService.orchestrate(any())) doAnswer {
            CompletableDeferred(TaskRefResponse("/tasks/${randomUUID()}"))
          }

          handler.delete(resource)
        }

        after {
          reset(cloudDriverService, orcaService)
        }

        test("it deletes the security group via Orca") {
          argumentCaptor<OrchestrationRequest>().apply {
            verify(orcaService).orchestrate(capture())
            expectThat(firstValue) {
              application.isEqualTo(securityGroup.application)
              job
                .hasSize(1)
                .first()
                .type
                .isEqualTo("deleteSecurityGroup")
            }
          }
        }
      }
    }
  }

  private fun <F : Fixture> F.resetServices() {
    reset(cloudDriverService, orcaService)
  }

  private fun <F : Fixture> F.resetVpc() {
    reset(cloudDriverCache)
  }

  private fun CurrentFixture.cloudDriverSecurityGroupReturns() {
    with(cloudDriverResponse) {
      whenever(
        cloudDriverService
          .getSecurityGroup(accountName, CLOUD_PROVIDER, name, region, vpcId)
      ) doReturn CompletableDeferred(this)
    }
  }

  private fun CurrentFixture.cloudDriverSecurityGroupNotFound() {
    with(cloudDriverResponse) {
      whenever(
        cloudDriverService
          .getSecurityGroup(accountName, CLOUD_PROVIDER, name, region, vpcId)
      ) doThrow RETROFIT_NOT_FOUND
    }
  }

  private fun <F : Fixture> F.setupVpc() {
    whenever(cloudDriverCache.networkBy(vpc.name, vpc.account, vpc.region)) doReturn vpc
    whenever(cloudDriverCache.networkBy(vpc.id)) doReturn vpc
  }

  val Fixture.resource: Resource<SecurityGroup>
    get() = Resource(
      apiVersion = SPINNAKER_API_V1,
      metadata = ResourceMetadata(
        name = with(securityGroup) {
          ResourceName("ec2.SecurityGroup:$application:$accountName:$region:$name")
        },
        uid = randomUID(),
        resourceVersion = 1234L
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
