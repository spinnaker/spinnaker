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
package com.netflix.spinnaker.keel.ec2.asset

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetMetadata
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.CidrSecurityGroupRule
import com.netflix.spinnaker.keel.api.ec2.PortRange
import com.netflix.spinnaker.keel.api.ec2.ReferenceSecurityGroupRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroup
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.TCP
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.EC2AssetPlugin
import com.netflix.spinnaker.keel.ec2.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRef
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.plugin.CurrentSuccess
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.oneeyedmen.minutest.junit.junitTests
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.util.*

@TestInstance(PER_CLASS)
internal object EC2AssetPluginTests {

  val cloudDriverService = mock<CloudDriverService>()
  val cloudDriverCache = mock<CloudDriverCache>()
  val orcaService = mock<OrcaService>()

  val objectMapper = ObjectMapper().registerKotlinModule()

  val subject = EC2AssetPlugin(
    cloudDriverService,
    cloudDriverCache,
    orcaService,
    objectMapper
  )

  val vpc = Network(CLOUD_PROVIDER, UUID.randomUUID().toString(), "vpc1", "prod", "us-west-3")
  @BeforeAll
  fun setUpVpcs() {
    whenever(cloudDriverCache.networkBy(vpc.name, vpc.account, vpc.region)) doReturn vpc
    whenever(cloudDriverCache.networkBy(vpc.id)) doReturn vpc
  }

  @AfterAll
  fun resetVpcs() {
    reset(cloudDriverCache)
  }

  @TestFactory
  fun `fetching security group status`() = junitTests<Unit> {
    val securityGroup = SecurityGroup(
      application = "keel",
      name = "fnord",
      accountName = vpc.account,
      region = vpc.region,
      vpcName = vpc.name,
      description = "dummy security group"
    )

    context("no matching security group exists") {
      before {
        securityGroup.apply {
          whenever(cloudDriverService.getSecurityGroup(
            accountName,
            CLOUD_PROVIDER,
            name,
            region,
            vpc.id
          )) doThrow RETROFIT_NOT_FOUND
        }
      }

      after {
        reset(cloudDriverService)
      }

      val request = Asset(
        apiVersion = SPINNAKER_API_V1,
        metadata = AssetMetadata(
          name = AssetName("ec2.SecurityGroup:keel:test:us-west-2:keel")
        ),
        kind = "ec2.SecurityGroup",
        spec = objectMapper.convertValue(securityGroup)
      )

      test("it returns null") {
        val response = subject.current(request)

        expectThat(response)
          .isA<CurrentSuccess>()
          .get { current }
          .isNull()
      }
    }

    context("a matching security group exists") {
      before {
        securityGroup.apply {
          val riverSecurityGroup = com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup(
            CLOUD_PROVIDER, UUID.randomUUID().toString(), name, description, accountName, region, vpc.id, emptySet(), Moniker(application)
          )
          whenever(cloudDriverService.getSecurityGroup(
            accountName,
            CLOUD_PROVIDER,
            name,
            region,
            vpc.id
          )) doReturn riverSecurityGroup
        }
      }

      after {
        reset(cloudDriverService)
      }

      val request = Asset(
        apiVersion = SPINNAKER_API_V1,
        metadata = AssetMetadata(
          name = AssetName("ec2.SecurityGroup:keel:test:us-west-2:keel")
        ),
        kind = "ec2.SecurityGroup",
        spec = objectMapper.convertValue(securityGroup)
      )

      test("it returns the security group") {
        val response = subject.current(request)

        expectThat(response)
          .isA<CurrentSuccess>()
          .and {
            get { current }
              .isNotNull()
              .get { objectMapper.convertValue<SecurityGroup>(spec) }
              .isEqualTo(securityGroup)
          }
          .and {
            get { objectMapper.convertValue<SecurityGroup>(desired.spec) }
              .isEqualTo(securityGroup)
          }
      }
    }
  }

  @TestFactory
  fun `converging a security group`() = junitTests<Unit> {
    val securityGroup = SecurityGroup(
      application = "keel",
      name = "fnord",
      accountName = vpc.account,
      region = vpc.region,
      vpcName = vpc.name,
      description = "dummy security group"
    )

    context("no rule partial assets provided") {
      before {
        whenever(orcaService.orchestrate(any())) doAnswer {
          TaskRefResponse(TaskRef(UUID.randomUUID().toString()))
        }
      }

      after {
        reset(cloudDriverService, orcaService)
      }

      val request = Asset(
        apiVersion = SPINNAKER_API_V1,
        metadata = AssetMetadata(
          name = AssetName("ec2.SecurityGroup:keel:test:us-west-2:keel")
        ),
        kind = "ec2.SecurityGroup",
        spec = objectMapper.convertValue(securityGroup)
      )

      test("it upserts the security group via Orca") {
        subject.upsert(request)

        argumentCaptor<OrchestrationRequest>().apply {
          verify(orcaService).orchestrate(capture())
          expectThat(firstValue) {
            application.isEqualTo(securityGroup.application)
            job.hasSize(1)
          }
        }
      }
    }
  }

  @TestFactory
  fun `converging a security group with an ingress rule`() = junitTests<Unit> {
    context("the referenced security group exists") {
      val securityGroup = SecurityGroup(
        application = "keel",
        name = "fnord",
        accountName = vpc.account,
        region = vpc.region,
        vpcName = vpc.name,
        description = "dummy security group"
      )

      context("an security group ingress rule") {
        before {
          whenever(orcaService.orchestrate(any())) doAnswer {
            TaskRefResponse(TaskRef(UUID.randomUUID().toString()))
          }
        }

        after {
          reset(cloudDriverService, orcaService)
        }

        val securityGroupRule = ReferenceSecurityGroupRule(
          protocol = TCP,
          account = "test",
          name = "otherapp",
          vpcName = "vpc0",
          portRange = PortRange(
            startPort = 443,
            endPort = 443
          )
        )

        val request = Asset(
          apiVersion = SPINNAKER_API_V1,
          metadata = AssetMetadata(
            name = AssetName("ec2.SecurityGroup:keel:test:us-west-2:keel")
          ),
          kind = "ec2.SecurityGroup",
          spec = objectMapper.convertValue(securityGroup.copy(inboundRules = listOf(securityGroupRule)))
        )

        test("it upserts the security group via Orca") {
          subject.upsert(request)

          argumentCaptor<OrchestrationRequest>().apply {
            verify(orcaService).orchestrate(capture())
            expectThat(firstValue) {
              application.isEqualTo(securityGroup.application)
              job.hasSize(1)
              job[0]["securityGroupIngress"].isA<List<*>>()
                .hasSize(1).first().isA<Map<String, *>>()
                .and {
                  get("type").isEqualTo(securityGroupRule.protocol.name)
                  get("startPort").isEqualTo(securityGroupRule.portRange.startPort)
                  get("endPort").isEqualTo(securityGroupRule.portRange.endPort)
                  get("name").isEqualTo(securityGroupRule.name)
                }
            }
          }
        }
      }

      context("an IP block range ingress rule") {
        before {
          whenever(orcaService.orchestrate(any())) doAnswer {
            TaskRefResponse(TaskRef(UUID.randomUUID().toString()))
          }
        }

        after{
          reset(cloudDriverService, orcaService)
        }

        val securityGroupRule = CidrSecurityGroupRule(
          protocol = TCP,
          blockRange = "10.0.0.0/16",
          portRange = PortRange(
            startPort = 443,
            endPort = 443
          )
        )

        val request = Asset(
          apiVersion = SPINNAKER_API_V1,
          metadata = AssetMetadata(
            name = AssetName("ec2.SecurityGroup:keel:test:us-west-2:keel")
          ),
          kind = "ec2.SecurityGroup",
          spec = objectMapper.convertValue(securityGroup.copy(inboundRules = listOf(securityGroupRule)))
        )

        test("it upserts the security group via Orca") {
          subject.upsert(request)

          argumentCaptor<OrchestrationRequest>().apply {
            verify(orcaService).orchestrate(capture())
            expectThat(firstValue) {
              application.isEqualTo(securityGroup.application)
              job.hasSize(1)
              job[0]["ipIngress"].isA<List<*>>()
                .hasSize(1).first().isA<Map<String, *>>()
                .and {
                  get("type").isEqualTo(securityGroupRule.protocol.name)
                  get("cidr").isEqualTo(securityGroupRule.blockRange)
                  get("startPort").isEqualTo(securityGroupRule.portRange.startPort)
                  get("endPort").isEqualTo(securityGroupRule.portRange.endPort)
                }
            }
          }
        }
      }
    }
  }
}

private val Assertion.Builder<OrchestrationRequest>.application: Assertion.Builder<String>
  get() = get(OrchestrationRequest::application)

private val Assertion.Builder<OrchestrationRequest>.job: Assertion.Builder<List<Job>>
  get() = get(OrchestrationRequest::job)
