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
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.Cluster
import com.netflix.spinnaker.keel.api.ec2.SecurityGroup
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.ec2.resource.AmazonSecurityGroupHandler
import com.netflix.spinnaker.keel.ec2.resource.ClusterHandler
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.ConvergeAccepted
import com.netflix.spinnaker.keel.plugin.ConvergeFailed
import com.netflix.spinnaker.keel.plugin.ConvergeResponse
import com.netflix.spinnaker.keel.plugin.CurrentResponse
import com.netflix.spinnaker.keel.plugin.ResourceError
import com.netflix.spinnaker.keel.plugin.ResourceMissing
import com.netflix.spinnaker.keel.plugin.ResourcePlugin
import com.netflix.spinnaker.keel.plugin.ResourceState
import org.slf4j.LoggerFactory
import java.time.Clock

class EC2ResourcePlugin(
  cloudDriverService: CloudDriverService,
  cloudDriverCache: CloudDriverCache,
  orcaService: OrcaService
) : ResourcePlugin {

  override val apiVersion: ApiVersion = SPINNAKER_API_V1.subApi("ec2")

  override val supportedKinds = mapOf(
    ResourceKind(apiVersion.group, "security-group", "security-groups") to SecurityGroup::class.java,
    ResourceKind(apiVersion.group, "security-group-rule", "security-group-rules") to SecurityGroupRule::class.java,
    ResourceKind(apiVersion.group, "cluster", "clusters") to Cluster::class.java
  )

  override fun current(request: Resource<*>): CurrentResponse {
    val spec = request.spec
    return when (spec) {
      is Cluster -> {
        @Suppress("UNCHECKED_CAST")
        val current = clusterHandler.current(spec, request as Resource<Cluster>)
        if (current == null) {
          ResourceMissing
        } else {
          ResourceState(current)
        }
      }
      is SecurityGroup -> {
        @Suppress("UNCHECKED_CAST")
        val current = securityGroupHandler.current(spec, request as Resource<SecurityGroup>)
        if (current == null) {
          ResourceMissing
        } else {
          ResourceState(current)
        }
      }
      else -> {
        val message = "Unsupported resource type ${request.kind} with id ${request.metadata.name}"
        log.error("Current failed: {}", message)
        ResourceError(message)
      }
    }
  }

  override fun upsert(request: Resource<*>): ConvergeResponse {
    val spec = request.spec
    return try {
      when (spec) {
        is Cluster -> {
          clusterHandler.converge(request.metadata.name, spec)
          ConvergeAccepted
        }
        is SecurityGroup -> {
          securityGroupHandler.converge(request.metadata.name, spec)
          ConvergeAccepted
        }
        else -> {
          val message = "Unsupported resource type ${request.kind} with id ${request.metadata.name}"
          log.error("Converge failed: {}", message)
          ConvergeFailed(message)
        }
      }
    } catch (e: Exception) {
      ConvergeFailed(
        e.message
          ?: "Caught ${e.javaClass.name} converging ${request.kind} with id ${request.metadata.name}"
      )
        .also {
          log.error(it.reason, e)
        }
    }
  }

  override fun delete(request: Resource<*>): ConvergeResponse {
    val spec = request.spec
    return try {
      when (spec) {
        is SecurityGroup -> {
          securityGroupHandler.delete(request.metadata.name, spec)
          ConvergeAccepted
        }
        else -> {
          val message = "Unsupported resource type ${request.kind} with id ${request.metadata.name}"
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

  private val clusterHandler =
    ClusterHandler(cloudDriverService, cloudDriverCache, orcaService, Clock.systemDefaultZone())

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
