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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.ec2.SecurityGroup
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.ec2.asset.AmazonSecurityGroupHandler
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.AssetPlugin
import com.netflix.spinnaker.keel.plugin.ConvergeAccepted
import com.netflix.spinnaker.keel.plugin.ConvergeFailed
import com.netflix.spinnaker.keel.plugin.ConvergeResponse
import com.netflix.spinnaker.keel.plugin.CurrentError
import com.netflix.spinnaker.keel.plugin.CurrentResponse
import com.netflix.spinnaker.keel.plugin.CurrentSuccess
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class EC2AssetPlugin(
  cloudDriverService: CloudDriverService,
  cloudDriverCache: CloudDriverCache,
  orcaService: OrcaService,
  private val objectMapper: ObjectMapper
) : AssetPlugin {

  override val supportedKinds: Iterable<String> = setOf(
    "ec2.SecurityGroup",
    "ec2.SecurityGroupRule",
    "ec2.ClassicLoadBalancer"
  )

  override fun current(request: Asset): CurrentResponse =
    when (request.kind) {
      "ec2.SecurityGroup" -> {
        val spec: SecurityGroup = objectMapper.convertValue(request.spec)
        val current = securityGroupHandler.current(spec, request)
        log.info("{} desired state: {}", request.id, spec)
        log.info("{} current state: {}", request.id, current?.spec)
        CurrentSuccess(request, current)
      }
      else -> {
        val message = "Unsupported asset type ${request.kind} with id ${request.id}"
        log.error("Current failed: {}", message)
        CurrentError(message)
      }
    }

  override fun upsert(request: Asset): ConvergeResponse =
    try {
      when (request.kind) {
        "ec2.SecurityGroup" -> {
          val spec: SecurityGroup = objectMapper.convertValue(request.spec)
          securityGroupHandler.converge(request.id, spec)
          ConvergeAccepted
        }
        else -> {
          val message = "Unsupported asset type ${request.kind} with id ${request.id}"
          log.error("Converge failed: {}", message)
          ConvergeFailed(message)
        }
      }
    } catch (e: Exception) {
      ConvergeFailed(e.message
        ?: "Caught ${e.javaClass.name} converging ${request.kind} with id ${request.id}")
    }

  override fun delete(request: Asset): ConvergeResponse =
    try {
      when (request.kind) {
        "ec2.SecurityGroup" -> {
          val spec: SecurityGroup = objectMapper.convertValue(request.spec)
          securityGroupHandler.delete(request.id, spec)
          ConvergeAccepted
        }
        else -> {
          val message = "Unsupported asset type ${request.kind} with id ${request.id}"
          log.error("Converge failed: {}", message)
          ConvergeFailed(message)
        }
      }
    } catch (e: Exception) {
      ConvergeFailed(e.message
        ?: "Caught ${e.javaClass.name} converging ${request.kind} with id ${request.id}")
    }

  private val securityGroupHandler =
    AmazonSecurityGroupHandler(cloudDriverService, cloudDriverCache, orcaService, objectMapper)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
