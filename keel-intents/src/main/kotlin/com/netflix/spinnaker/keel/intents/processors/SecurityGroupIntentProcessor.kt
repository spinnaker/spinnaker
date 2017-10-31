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
import com.netflix.spinnaker.keel.intents.ANY_MAP_TYPE
import com.netflix.spinnaker.keel.intents.AmazonSecurityGroupSpec
import com.netflix.spinnaker.keel.intents.SecurityGroupIntent
import com.netflix.spinnaker.keel.intents.SecurityGroupSpec
import com.netflix.spinnaker.keel.intents.processors.converters.securitygroups.SecurityGroupConverter
import com.netflix.spinnaker.keel.model.Job
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
    log.info("Converging state for ${intent.spec.accountName}:${intent.spec.region}:${intent.spec.name}")

    val currentState = getSecurityGroup(intent.spec)

    traceRepository.record(Trace(
      startingState = if (currentState == null) mapOf() else objectMapper.convertValue(currentState, ANY_MAP_TYPE),
      intent = intent
    ))

    // TODO rz - Need a way to communicate reasons for no-ops in the case of a dry-run. Perhaps the signature of
    // converge should be changed to use a ConvergeResult(orchestrations, currentState, reason) or something?
    val securityGroup = securityGroupConverter.convertToJob(intent.spec)
    if (currentState != null) {
      if (securityGroup == currentState) {
        return ConvergeResult(listOf(), ConvergeReason.UNCHANGED.reason)
      }

      // If any upstream security groups don't exist, don't try to converge on this state yet.
      val missingUpstreamGroups = currentState.inboundRules
        .filter { it.securityGroup != null }
        .map { securityGroupConverter.convertFromState(it.securityGroup!!) }
        .map { getSecurityGroup(it) }
        .count { it == null }

      if (missingUpstreamGroups > 0) {
        return ConvergeResult(listOf(), "Some upstream security groups are missing")
      }
    }

    return ConvergeResult(listOf(
      OrchestrationRequest(
        name = "Upsert security group",
        application = intent.spec.application,
        description = "Converging on desired security group state",
        job = listOf(
          Job(
            type = "upsertSecurityGroup",
            m = objectMapper.convertValue(securityGroup, ANY_MAP_TYPE)
          )
        ),
        trigger = Trigger(intent.getId())
      )
    ))
  }

  private fun getSecurityGroup(spec: SecurityGroupSpec): SecurityGroup? {
    // TODO rz - Quite dislike that any new cloudprovider would need to edit keel for declarative support... how to fix?
    if (spec is AmazonSecurityGroupSpec) {
      try {
        return if (spec.vpcId == null) {
          clouddriverService.getSecurityGroup(spec.accountName, "aws", spec.name, spec.region)
        } else {
          clouddriverService.getSecurityGroup(spec.accountName, "aws", spec.name, spec.region, spec.vpcId)
        }
      } catch (e: RetrofitError) {
        if (e.notFound()) {
          return null
        }
        throw e
      }
    }
    throw NotImplementedError("Only amazon security groups are supported at the moment")
  }
}
