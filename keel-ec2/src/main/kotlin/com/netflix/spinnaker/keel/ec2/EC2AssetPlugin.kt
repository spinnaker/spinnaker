/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.ec2

import com.netflix.spinnaker.keel.api.AssetContainer
import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.plugin.ConvergeResponse
import com.netflix.spinnaker.keel.api.plugin.ConvergeStatus.ACCEPTED
import com.netflix.spinnaker.keel.api.plugin.ConvergeStatus.ERROR
import com.netflix.spinnaker.keel.api.plugin.CurrentResponse
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.ec2.asset.AmazonSecurityGroupHandler
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.AssetPlugin
import com.netflix.spinnaker.keel.proto.isA
import com.netflix.spinnaker.keel.proto.unpack
import io.grpc.stub.StreamObserver
import org.lognet.springboot.grpc.GRpcService
import org.slf4j.LoggerFactory

@GRpcService
class EC2AssetPlugin(
  cloudDriverService: CloudDriverService,
  cloudDriverCache: CloudDriverCache,
  orcaService: OrcaService
) : AssetPlugin() {

  override val supportedTypes: Iterable<TypeMetadata> = setOf(
    "ec2.SecurityGroup",
    "ec2.SecurityGroupRule",
    "ec2.ClassicLoadBalancer"
  ).map { kind ->
    TypeMetadata.newBuilder().setApiVersion("1.0").setKind(kind).build()
  }

  override fun current(request: AssetContainer, responseObserver: StreamObserver<CurrentResponse>) {
    when {
      request.asset.spec.isA<SecurityGroup>() -> {
        val spec: SecurityGroup = request.asset.spec.unpack()
        val assetPair = securityGroupHandler.run {
          Pair(
            current(spec, request),
            flattenAssetContainer(request)
          )
        }
        log.info("{} requested state: {}", request.asset.id, spec)
        log.info("{} current state: {}", request.asset.id, assetPair.first?.spec?.unpack<SecurityGroup>())
        log.info("{} desired state: {}", request.asset.id, assetPair.second.spec?.unpack<SecurityGroup>())
        with(responseObserver) {
          onNext(CurrentResponse
            .newBuilder()
            .apply {
              if (assetPair.first != null) {
                successBuilder.current = assetPair.first
              }
              successBuilder.desired = assetPair.second
            }
            .build()
          )
          onCompleted()
        }
      }
      else -> {
        val message = "Unsupported asset type ${request.asset.spec.typeUrl} with id ${request.asset.id.value}"
        log.error("Current failed: {}", message)
        with(responseObserver) {
          onNext(CurrentResponse
            .newBuilder()
            .apply {
              failureBuilder.reason = message
            }
            .build()
          )
          onCompleted()
        }
      }
    }

  }

  override fun converge(request: AssetContainer, responseObserver: StreamObserver<ConvergeResponse>) {
    try {
      when {
        request.asset.spec.isA<SecurityGroup>() -> {
          securityGroupHandler.let {
            val flattenedAsset = it.flattenAssetContainer(request)
            val spec: SecurityGroup = flattenedAsset.spec.unpack()
            it.converge(flattenedAsset.id.value, spec)
          }
          with(responseObserver) {
            onNext(ConvergeResponse.newBuilder()
              .apply { status = ACCEPTED }
              .build())
            onCompleted()
          }
        }
        else -> {
          val message = "Unsupported asset type ${request.asset.spec.typeUrl} with id ${request.asset.id.value}"
          log.error("Converge failed: {}", message)
          with(responseObserver) {
            onNext(ConvergeResponse.newBuilder()
              .apply {
                status = ERROR
                failureBuilder.reason = message
              }
              .build())
            onCompleted()
          }
        }
      }

    } catch (e: Exception) {
      with(responseObserver) {
        onNext(ConvergeResponse.newBuilder()
          .apply {
            status = ERROR
            failureBuilder.reason = e.message
          }
          .build())
        onCompleted()
      }
    }
  }

  private val securityGroupHandler =
    AmazonSecurityGroupHandler(cloudDriverService, cloudDriverCache, orcaService)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
