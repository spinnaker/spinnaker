/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.intents.processors

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.*
import com.netflix.spinnaker.keel.clouddriver.ClouddriverService
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.intents.*
import com.netflix.spinnaker.keel.intents.processors.converters.SecurityGroupConverter
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.Trigger
import com.netflix.spinnaker.keel.tracing.Trace
import com.netflix.spinnaker.keel.tracing.TraceRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
class SecurityGroupIntentProcessor
@Autowired constructor(
  private val traceRepository: TraceRepository,
  private val clouddriverService: ClouddriverService,
  private val objectMapper: ObjectMapper,
  private val securityGroupConverter: SecurityGroupConverter
) : IntentProcessor<SecurityGroupIntent> {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun supports(intent: Intent<IntentSpec>) = intent is SecurityGroupIntent

  override fun converge(intent: SecurityGroupIntent): ConvergeResult {
    log.info("Converging state for ${intent.id}")

    val currentState = getSecurityGroups(intent.spec)

    traceRepository.record(Trace(
      startingState = objectMapper.convertValue(mapOf("state" to currentState), ANY_MAP_TYPE),
      intent = intent
    ))

    val desiredState = securityGroupConverter.convertToState(intent.spec)

    if (currentStateUpToDate(currentState, desiredState)) {
      return ConvergeResult(listOf(), ConvergeReason.UNCHANGED.reason)
    }

    if (missingUpstreamGroups(intent.spec)) {
      // TODO rz - Should return _what_ security groups are missing
      return ConvergeResult(listOf(), "Some upstream security groups are missing")
    }

    return ConvergeResult(listOf(
        OrchestrationRequest(
          name = "Upsert security group",
          application = intent.spec.application,
          description = "Converging on desired security group state",
          job = securityGroupConverter.convertToJob(intent.spec),
          trigger = Trigger(intent.id)
        )
      ),
      ConvergeReason.CHANGED.reason
    )
  }

  private fun getSecurityGroups(spec: SecurityGroupSpec): Set<SecurityGroup> {
    // TODO rz - Quite dislike that any new cloudprovider would need to edit keel for declarative support... how to fix?
    // Maybe this is the sacrifice we need to make to start statically typing all of our models?
    if (spec is AmazonSecurityGroupSpec) {
      return spec.regions
        .map { region ->
          try {
            return@map if (spec.vpcName == null) {
              clouddriverService.getSecurityGroup(spec.accountName, "aws", spec.name, region)
            } else {
              // TODO rz - vpc name work with vpc id?
              clouddriverService.getSecurityGroup(spec.accountName, "aws", spec.name, region, spec.vpcName)
            }
          } catch (e: RetrofitError) {
            if (e.notFound()) {
              return@map null
            }
            throw e
          }
        }
        .filterNotNull()
        .toSet()
    }
    throw NotImplementedError("Only amazon security groups are supported at the moment")
  }

  private fun currentStateUpToDate(currentState: Set<SecurityGroup>, desiredState: Set<SecurityGroup>): Boolean {
    log.warn("Current state update check is not implemented")
    return false
  }

  private fun missingUpstreamGroups(spec: SecurityGroupSpec): Boolean {
    spec.inboundRules
      .filterIsInstance<ReferenceSecurityGroupRule>()
      .forEach {
        spec.regions.forEach { region ->
          clouddriverService.getSecurityGroup(spec.accountName, "aws", it.name, region) ?: return true
        }
      }
    return false
  }
}
