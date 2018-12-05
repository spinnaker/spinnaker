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

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetKind
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.SecurityGroup
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.ec2.asset.AmazonSecurityGroupHandler
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.AssetPlugin
import com.netflix.spinnaker.keel.plugin.ConvergeAccepted
import com.netflix.spinnaker.keel.plugin.ConvergeFailed
import com.netflix.spinnaker.keel.plugin.ConvergeResponse
import com.netflix.spinnaker.keel.plugin.CurrentResponse
import com.netflix.spinnaker.keel.plugin.ResourceError
import com.netflix.spinnaker.keel.plugin.ResourceMissing
import com.netflix.spinnaker.keel.plugin.ResourceState
import org.slf4j.LoggerFactory

class EC2AssetPlugin(
  cloudDriverService: CloudDriverService,
  cloudDriverCache: CloudDriverCache,
  orcaService: OrcaService
) : AssetPlugin {

  override val apiVersion: ApiVersion = SPINNAKER_API_V1.subApi("ec2")

  override val supportedKinds = mapOf(
    AssetKind(apiVersion.group, "security-group", "security-groups") to SecurityGroup::class.java,
    AssetKind(apiVersion.group, "security-group-rule", "security-group-rules") to SecurityGroupRule::class.java
  )

  override fun current(request: Asset<*>): CurrentResponse {
    val spec = request.spec
    return when (spec) {
      is SecurityGroup -> {
        @Suppress("UNCHECKED_CAST")
        val current = securityGroupHandler.current(spec, request as Asset<SecurityGroup>)
        log.info("{} desired state: {}", request.metadata.name, spec)
        log.info("{} current state: {}", request.metadata.name, current)
        if (current == null) {
          ResourceMissing
        } else {
          ResourceState(current)
        }
      }
      else -> {
        val message = "Unsupported asset type ${request.kind} with id ${request.metadata.name}"
        log.error("Current failed: {}", message)
        ResourceError(message)
      }
    }
  }

  override fun upsert(request: Asset<*>): ConvergeResponse {
    val spec = request.spec
    return try {
      when (spec) {
        is SecurityGroup -> {
          securityGroupHandler.converge(request.metadata.name, spec)
          ConvergeAccepted
        }
        else -> {
          val message = "Unsupported asset type ${request.kind} with id ${request.metadata.name}"
          log.error("Converge failed: {}", message)
          ConvergeFailed(message)
        }
      }
    } catch (e: Exception) {
      ConvergeFailed(e.message
        ?: "Caught ${e.javaClass.name} converging ${request.kind} with id ${request.metadata.name}")
    }
  }

  override fun delete(request: Asset<*>): ConvergeResponse {
    val spec = request.spec
    return try {
      when (spec) {
        is SecurityGroup -> {
          securityGroupHandler.delete(request.metadata.name, spec)
          ConvergeAccepted
        }
        else -> {
          val message = "Unsupported asset type ${request.kind} with id ${request.metadata.name}"
          log.error("Converge failed: {}", message)
          ConvergeFailed(message)
        }
      }
    } catch (e: Exception) {
      ConvergeFailed(e.message
        ?: "Caught ${e.javaClass.name} converging ${request.kind} with id ${request.metadata.name}")
    }
  }

  private val securityGroupHandler =
    AmazonSecurityGroupHandler(cloudDriverService, cloudDriverCache, orcaService)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
