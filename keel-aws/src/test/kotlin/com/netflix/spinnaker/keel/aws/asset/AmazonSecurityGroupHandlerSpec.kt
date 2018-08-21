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
package com.netflix.spinnaker.keel.aws.asset

import com.netflix.spinnaker.keel.api.AssetContainer
import com.netflix.spinnaker.keel.api.GrpcStubManager
import com.netflix.spinnaker.keel.api.PartialAsset
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import com.netflix.spinnaker.keel.aws.AmazonAssetPlugin
import com.netflix.spinnaker.keel.aws.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.aws.CidrRule
import com.netflix.spinnaker.keel.aws.PortRange
import com.netflix.spinnaker.keel.aws.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.aws.ReferenceRule
import com.netflix.spinnaker.keel.aws.SecurityGroup
import com.netflix.spinnaker.keel.aws.SecurityGroupRule
import com.netflix.spinnaker.keel.aws.SecurityGroupRules
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.proto.pack
import com.nhaarman.mockito_kotlin.argumentCaptor
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
import strikt.api.expect
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import strikt.protobuf.unpack
import strikt.protobuf.unpacksTo
import java.util.UUID

internal object AmazonSecurityGroupHandlerSpec : Spek({

  val grpc = GrpcStubManager(AssetPluginGrpc::newBlockingStub)

  val cloudDriverService = mock<CloudDriverService>()
  val cloudDriverCache = mock<CloudDriverCache>()
  val orcaService = mock<OrcaService>()

  beforeGroup {
    grpc.startServer {
      addService(AmazonAssetPlugin(cloudDriverService, cloudDriverCache, orcaService))
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
              kind = "aws.SecurityGroup"
              apiVersion = "1.0"
            }
            specBuilder.apply {
              value = securityGroup.toByteString()
            }
          }
            .build()
        }
        .build()

      val response by memoized {
        grpc.withChannel { stub -> stub.current(request) }
      }

      it("returns null") {
        expect(response.hasCurrent()).isFalse()
        expect(response.hasDesired()).isTrue()
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
              kind = "aws.SecurityGroup"
              apiVersion = "1.0"
            }
            spec = securityGroup.pack()
          }.build()
        }
        .build()


      val response by memoized {
        grpc.withChannel { stub -> stub.current(request) }
      }

      it("returns the security group") {
        expect(response) {
          map { it.hasCurrent() }.isTrue()
          map { it.current.spec }
            .unpacksTo<SecurityGroup>()
            .unpack<SecurityGroup>()
            .isEqualTo(securityGroup)

          map { it.hasDesired() }.isTrue()
          map { it.desired.spec }
            .unpacksTo<SecurityGroup>()
            .unpack<SecurityGroup>()
            .isEqualTo(securityGroup)
        }
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
        inboundRulesOrBuilderList.apply {
          addInboundRules(SecurityGroupRule.newBuilder().setReferenceRule(
            ReferenceRule.newBuilder().apply {
              protocol = "tcp"
              name = "otherapp"
              addPortRanges(PortRange.newBuilder().apply {
                startPort = 8080
                endPort = 8081
              })
            }
          ))
        }
      }
      .build()

    given("no rule partial assets provided") {
      afterGroup {
        reset(cloudDriverService, orcaService)
      }

      val request = AssetContainer
        .newBuilder()
        .apply {
          assetBuilder.apply {
            typeMetadataBuilder.apply {
              kind = "aws.SecurityGroup"
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
          expect(firstValue) {
            application.isEqualTo(securityGroup.application)
            job.hasSize(1)
          }
        }
      }
    }

    given("rule partial assets provided") {
      afterGroup {
        reset(cloudDriverService, orcaService)
      }

      val securityGroupRule = SecurityGroupRules.newBuilder()
        .apply {
          inboundRulesOrBuilderList.apply {
            addInboundRules(SecurityGroupRule.newBuilder().setCidrRule(
              CidrRule.newBuilder().apply {
                protocol = "tcp"
                blockRange = "10.0.0.0/16"
                addPortRanges(PortRange.newBuilder().apply {
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
              kind = "aws.SecurityGroup"
              apiVersion = "1.0"
            }
            spec = securityGroup.pack()
          }
          partialAssetsOrBuilderList.apply {
            addPartialAssets(PartialAsset.newBuilder().apply {
              idBuilder.value = "id"
              typeMetadataBuilder.apply {
                kind = "aws.SecurityGroupRule"
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
          expect(firstValue) {
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
  get() = map(OrchestrationRequest::application)

private val Assertion.Builder<OrchestrationRequest>.job: Assertion.Builder<List<Job>>
  get() = map(OrchestrationRequest::job)
