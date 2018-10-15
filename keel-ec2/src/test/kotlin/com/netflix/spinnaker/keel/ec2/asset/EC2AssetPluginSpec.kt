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

import com.netflix.spinnaker.keel.api.AssetContainer
import com.netflix.spinnaker.keel.api.GrpcStubManager
import com.netflix.spinnaker.keel.api.PartialAsset
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.CidrRule
import com.netflix.spinnaker.keel.ec2.EC2AssetPlugin
import com.netflix.spinnaker.keel.ec2.PortRange
import com.netflix.spinnaker.keel.ec2.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.ec2.ReferenceRule
import com.netflix.spinnaker.keel.ec2.SecurityGroup
import com.netflix.spinnaker.keel.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.ec2.SecurityGroupRules
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRef
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.proto.pack
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.contentEquals
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import strikt.protobuf.unpack
import strikt.protobuf.unpacksTo
import java.util.*

internal object EC2AssetPluginSpec : Spek({

  val grpc = GrpcStubManager(AssetPluginGrpc::newBlockingStub)

  val cloudDriverService = mock<CloudDriverService>()
  val cloudDriverCache = mock<CloudDriverCache>()
  val orcaService = mock<OrcaService>()

  beforeGroup {
    grpc.startServer {
      addService(EC2AssetPlugin(cloudDriverService, cloudDriverCache, orcaService))
    }
  }

  afterGroup(grpc::stopServer)

  val vpc = Network(CLOUD_PROVIDER, UUID.randomUUID().toString(), "vpc1", "prod", "us-west-3")
  beforeGroup {
    whenever(cloudDriverCache.networkBy(vpc.name, vpc.account, vpc.region)) doReturn vpc
    whenever(cloudDriverCache.networkBy(vpc.id)) doReturn vpc
  }

  afterGroup {
    reset(cloudDriverCache)
  }

  describe("fetch security group status") {
    val securityGroup = SecurityGroup.newBuilder()
      .apply {
        name = "fnord"
        accountName = vpc.account
        region = vpc.region
        vpcName = vpc.name
      }
      .build()

    given("no matching security group exists") {
      beforeGroup {
        securityGroup.apply {
          whenever(cloudDriverService.getSecurityGroup(accountName, CLOUD_PROVIDER, name, region, vpc.id)) doThrow RETROFIT_NOT_FOUND
        }
      }

      afterGroup {
        reset(cloudDriverService)
      }

      val request = AssetContainer
        .newBuilder()
        .apply {
          asset = assetBuilder.apply {
            typeMetadataBuilder.apply {
              kind = "ec2.SecurityGroup"
              apiVersion = "1.0"
            }
            spec = securityGroup.pack()
          }
            .build()
        }
        .build()

      val response by memoized {
        grpc.withChannel { stub -> stub.current(request) }
      }

      it("returns null") {
        expectThat(response) {
          get { hasSuccess() }.isTrue()
        }.and {
          get { success.hasCurrent() }.isFalse()
          get { success.hasDesired() }.isTrue()
        }
      }
    }

    given("a matching security group exists") {
      beforeGroup {
        securityGroup.apply {
          whenever(cloudDriverService.getSecurityGroup(accountName, CLOUD_PROVIDER, name, region, vpc.id)) doReturn com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup(
            CLOUD_PROVIDER, UUID.randomUUID().toString(), name, description, accountName, region, vpc.id, emptySet(), Moniker(application)
          )
        }
      }

      afterGroup {
        reset(cloudDriverService)
      }

      val request = AssetContainer
        .newBuilder()
        .apply {
          asset = assetBuilder.apply {
            typeMetadataBuilder.apply {
              kind = "ec2.SecurityGroup"
              apiVersion = "1.0"
            }
            spec = securityGroup.pack()
          }.build()
        }
        .build()


      val response by memoized {
        grpc.withChannel { stub ->
          stub.current(request)
        }
      }

      it("returns the security group") {
        expectThat(response) {
          get { hasSuccess() }.isTrue()
        }.and {
          get { success.hasCurrent() }.isTrue()
          get { success.current.spec }
            .unpacksTo<SecurityGroup>()
            .unpack<SecurityGroup>()
            .isEqualTo(securityGroup)

          get { success.hasDesired() }.isTrue()
          get { success.desired.spec }
            .unpacksTo<SecurityGroup>()
            .unpack<SecurityGroup>()
            .isEqualTo(securityGroup)
        }
      }
    }

    given("a matching security group with rules exists") {
      val securityGroupWithRules = SecurityGroup.newBuilder()
        .apply {
          name = "fnord"
          accountName = vpc.account
          region = vpc.region
          vpcName = vpc.name

          // add rules for a bunch of ports in no particular order
          listOf(6565, 1337, 80, 8081).forEach { port ->
            addInboundRule(
              SecurityGroupRule.newBuilder().apply {
                selfReferencingRuleBuilder.apply {
                  protocol = "tcp"
                  addPortRange(
                    PortRange.newBuilder().apply {
                      startPort = port
                      endPort = port
                    }
                  )
                }
              }
            )
          }
        }
        .build()

      beforeGroup {
        securityGroup.apply {
          whenever(cloudDriverService.getSecurityGroup(accountName, CLOUD_PROVIDER, name, region, vpc.id)) doReturn com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup(
            CLOUD_PROVIDER, UUID.randomUUID().toString(), name, description, accountName, region, vpc.id,
            securityGroupWithRules.inboundRuleList.reversed().map { rule ->
              com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup.SecurityGroupRule(
                rule.selfReferencingRule.protocol,
                rule.selfReferencingRule.portRangeList.reversed().map {
                  com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup.SecurityGroupRulePortRange(it.startPort, it.endPort)
                },
                com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup.SecurityGroupRuleReference(name, accountName, region, vpc.id),
                null
              )
            }.toSet(),
            Moniker(application)
          )
        }
      }

      afterGroup {
        reset(cloudDriverService)
      }

      val request = AssetContainer
        .newBuilder()
        .apply {
          asset = assetBuilder.apply {
            typeMetadataBuilder.apply {
              kind = "ec2.SecurityGroup"
              apiVersion = "1.0"
            }
            spec = securityGroupWithRules.pack()
          }.build()
        }
        .build()


      val response by memoized {
        grpc.withChannel { stub ->
          stub.current(request)
        }
      }

      it("returns consistent models of the security group") {
        val current = response.success.current
        val desired = response.success.desired
        expectThat(current.spec.toByteArray()).contentEquals(desired.spec.toByteArray())
      }
    }
  }

  describe("converging a security group") {
    val securityGroup = SecurityGroup.newBuilder()
      .apply {
        name = "fnord"
        accountName = vpc.account
        region = vpc.region
        vpcName = vpc.name
        inboundRuleOrBuilderList.apply {
          addInboundRule(SecurityGroupRule.newBuilder().setReferenceRule(
            ReferenceRule.newBuilder().apply {
              protocol = "tcp"
              name = "otherapp"
              addPortRange(PortRange.newBuilder().apply {
                startPort = 8080
                endPort = 8081
              })
            }
          ))
        }
      }
      .build()

    given("no rule partial assets provided") {
      beforeGroup {
        whenever(orcaService.orchestrate(any())) doAnswer {
          TaskRefResponse(TaskRef(UUID.randomUUID().toString()))
        }
      }

      afterGroup {
        reset(cloudDriverService, orcaService)
      }

      val request = AssetContainer
        .newBuilder()
        .apply {
          assetBuilder.apply {
            typeMetadataBuilder.apply {
              kind = "ec2.SecurityGroup"
              apiVersion = "1.0"
            }
            spec = securityGroup.pack()
          }
        }
        .build()

      on("converge request") {
        grpc.withChannel { stub ->
          stub.converge(request)
        }
      }

      it("upserts the security group via Orca") {
        argumentCaptor<OrchestrationRequest>().apply {
          verify(orcaService).orchestrate(capture())
          expectThat(firstValue) {
            application.isEqualTo(securityGroup.application)
            job.hasSize(1)
          }
        }
      }
    }

    given("rule partial assets provided") {
      beforeGroup {
        whenever(orcaService.orchestrate(any())) doAnswer {
          TaskRefResponse(TaskRef(UUID.randomUUID().toString()))
        }
      }

      afterGroup {
        reset(cloudDriverService, orcaService)
      }

      val securityGroupRule = SecurityGroupRules.newBuilder()
        .apply {
          inboundRuleOrBuilderList.apply {
            addInboundRule(SecurityGroupRule.newBuilder().setCidrRule(
              CidrRule.newBuilder().apply {
                protocol = "tcp"
                blockRange = "10.0.0.0/16"
                addPortRange(PortRange.newBuilder().apply {
                  startPort = 443
                  endPort = 443
                })
              }
            ))
          }
        }
        .build()

      val request = AssetContainer
        .newBuilder()
        .apply {
          assetBuilder.apply {
            idBuilder.value = "id"
            typeMetadataBuilder.apply {
              kind = "ec2.SecurityGroup"
              apiVersion = "1.0"
            }
            spec = securityGroup.pack()
          }
          partialAssetOrBuilderList.apply {
            addPartialAsset(PartialAsset.newBuilder().apply {
              idBuilder.value = "id"
              typeMetadataBuilder.apply {
                kind = "ec2.SecurityGroupRule"
                apiVersion = "1.0"
              }
              spec = securityGroupRule.pack()
            })
          }
        }
        .build()

      on("converge request") {
        grpc.withChannel { stub ->
          stub.converge(request)
        }
      }

      it("upserts the security group via Orca") {
        argumentCaptor<OrchestrationRequest>().apply {
          verify(orcaService).orchestrate(capture())
          expectThat(firstValue) {
            application.isEqualTo(securityGroup.application)
            job.hasSize(1)
            job[0]["securityGroupIngress"].isA<List<*>>()
              .hasSize(1).first().isA<Map<String, *>>()
              .and {
                get("type").isEqualTo("tcp")
                get("startPort").isEqualTo(8080)
                get("endPort").isEqualTo(8081)
                get("name").isEqualTo("otherapp")
              }
            job[0]["ipIngress"].isA<List<*>>()
              .hasSize(1).first().isA<Map<String, *>>()
              .and {
                get("type").isEqualTo("tcp")
                get("cidr").isEqualTo("10.0.0.0/16")
                get("startPort").isEqualTo(443)
                get("endPort").isEqualTo(443)
              }
          }
        }
      }
    }
  }
})

private val Assertion.Builder<OrchestrationRequest>.application: Assertion.Builder<String>
  get() = get(OrchestrationRequest::application)

private val Assertion.Builder<OrchestrationRequest>.job: Assertion.Builder<List<Job>>
  get() = get(OrchestrationRequest::job)
