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
package com.netflix.spinnaker.keel.intent.securitygroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.ConvergeReason
import com.netflix.spinnaker.keel.ConvergeResult
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentProcessor
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.dryrun.ChangeType
import com.netflix.spinnaker.keel.exceptions.DeclarativeException
import com.netflix.spinnaker.keel.intent.ANY_MAP_TYPE
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.state.StateInspector
import com.netflix.spinnaker.keel.tracing.Trace
import com.netflix.spinnaker.keel.tracing.TraceRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class SecurityGroupIntentProcessor
@Autowired constructor(
  private val traceRepository: TraceRepository,
  private val objectMapper: ObjectMapper,
  private val securityGroupConverters: List<SecurityGroupConverter<SecurityGroupSpec>>,
  private val securityGroupLoaders: List<SecurityGroupLoader>
) : IntentProcessor<SecurityGroupIntent> {

  override fun supports(intent: Intent<IntentSpec>) = intent is SecurityGroupIntent

  override fun converge(intent: SecurityGroupIntent): ConvergeResult {
    val converter = converterForSpec(intent.spec)

    val changeSummary = ChangeSummary(intent.id())
    val currentState = intent.spec.loadSystemState()

    traceRepository.record(Trace(
      startingState = objectMapper.convertValue(mapOf("state" to currentState), ANY_MAP_TYPE),
      intent = intent
    ))

    if (currentStateUpToDate(converter, intent.id(), currentState, intent.spec, changeSummary)) {
      changeSummary.addMessage(ConvergeReason.UNCHANGED.reason)
      return ConvergeResult(listOf(), changeSummary)
    }

    val missingGroups = missingUpstreamGroups(intent.spec)
    if (missingGroups.isNotEmpty()) {
      changeSummary.addMessage("Some upstream security groups are missing: $missingGroups")
      changeSummary.type = ChangeType.FAILED_PRECONDITIONS
      return ConvergeResult(listOf(), changeSummary)
    }

    changeSummary.type = if (currentState == null) ChangeType.CREATE else ChangeType.UPDATE

    return ConvergeResult(listOf(
        OrchestrationRequest(
          name = "Upsert security group",
          application = intent.spec.application,
          description = "Converging on desired security group state",
          job = converter.convertToJob(intent.spec, changeSummary),
          trigger = OrchestrationTrigger(intent.id())
        )
      ),
      changeSummary
    )
  }

  private fun currentStateUpToDate(converter: SecurityGroupConverter<SecurityGroupSpec>,
                                   intentId: String,
                                   currentState: SecurityGroup?,
                                   desiredState: SecurityGroupSpec,
                                   changeSummary: ChangeSummary): Boolean {
    if (currentState == null) {
      return false
    }

    val diff = StateInspector(objectMapper).getDiff(
      intentId = intentId,
      currentState = currentState,
      desiredState = converter.convertToState(desiredState),
      modelClass = SecurityGroup::class,
      specClass = SecurityGroupSpec::class,
      ignoreKeys = setOf("type", "id", "moniker", "summary", "description")
    )
    changeSummary.diff = diff
    return diff.isEmpty()
  }

  private fun missingUpstreamGroups(spec: SecurityGroupSpec): List<String> {
    return spec.inboundRules
      .filterIsInstance<NamedReferenceSupport>()
      .filter {
        spec.name != it.name
      }
      .map {
        return@map if (loaderForSpec(spec).upstreamGroup(spec, it.name) == null) it.name else null
      }
      .filterNotNull()
      .distinct()
  }

  private fun converterForSpec(spec: SecurityGroupSpec): SecurityGroupConverter<SecurityGroupSpec> =
    securityGroupConverters
      .firstOrNull { it.supports(spec) }
      ?: throw DeclarativeException("No SecurityGroupConverter found supporting ${spec.javaClass.simpleName}")

  private fun SecurityGroupSpec.loadSystemState(): SecurityGroup? =
    loaderForSpec(this).load(this)

  private fun loaderForSpec(spec: SecurityGroupSpec): SecurityGroupLoader =
    securityGroupLoaders
      .firstOrNull { it.supports(spec) }
      ?: throw DeclarativeException("No SecurityGroupLoader found supporting ${spec.javaClass.simpleName}")
}
