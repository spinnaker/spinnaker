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
package com.netflix.spinnaker.keel.intent.aws.securitygroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.*
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.dryrun.ChangeType
import com.netflix.spinnaker.keel.exceptions.DeclarativeException
import com.netflix.spinnaker.keel.intent.ANY_MAP_TYPE
import com.netflix.spinnaker.keel.intent.NamedReferenceSupport
import com.netflix.spinnaker.keel.intent.SecurityGroupSpec
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.state.StateInspector
import com.netflix.spinnaker.keel.tracing.Trace
import com.netflix.spinnaker.keel.tracing.TraceRepository
import org.springframework.stereotype.Component

/**
 * Handles both full security group intents, as well as individual security
 * group rules.
 *
 * Externally-defined security group rules can be created against a root
 * intent, or a security group that isn't managed declaratively. This allows
 * different organizations to modify security group permissions independently
 * of each other, as well as incrementally convert to intent-based permissions.
 *
 * TODO rz - Root intents should be able to provide policies of who (or what)
 * can actually create rules against it, if anyone. By default, access will be
 * left open.
 */
@Component
class AmazonSecurityGroupIntentProcessor(
  private val traceRepository: TraceRepository,
  private val intentRepository: IntentRepository,
  private val objectMapper: ObjectMapper,
  private val converter: AmazonSecurityGroupConverter,
  private val loader: AmazonSecurityGroupLoader
) : IntentProcessor<AmazonSecurityGroupIntent> {

  override fun supports(intent: Intent<IntentSpec>) = intent is AmazonSecurityGroupIntent

  override fun converge(intent: AmazonSecurityGroupIntent): ConvergeResult {
    val changeSummary = ChangeSummary(intent.id)
    val currentState = loader.load(intent.spec)

    traceRepository.record(Trace(
      startingState = objectMapper.convertValue(mapOf("state" to currentState), ANY_MAP_TYPE),
      intent = intent
    ))

    // This processor handles both root security groups, as well as individual rules. If a rule is passed in, we
    // need to source the root intent (or fake it out if it doesn't exist).
    var rootIntent = getRootIntent(intent)
    if (rootIntent == null) {
      // There's no technical reason we can't create a security group from a single rule intent, but that would mean
      // the description would not be set and could not change in the future.
      if (currentState == null) {
        changeSummary.type = ChangeType.FAILED_PRECONDITIONS
        changeSummary.addMessage("Target security group does not exist, nor does a resource intent exist")
        return ConvergeResult(listOf(), changeSummary)
      }

      val transientSpec = converter.convertFromState(currentState)
        ?: throw DeclarativeException("Spec converted from current state was null for intent: ${intent.id}")

      rootIntent = AmazonSecurityGroupIntent(transientSpec)
    }

    val desiredRootIntent = mergeRootIntent(rootIntent, intent)

    if (currentStateUpToDate(intent.id, currentState, desiredRootIntent.spec, changeSummary)) {
      changeSummary.addMessage(ConvergeReason.UNCHANGED.reason)
      return ConvergeResult(listOf(), changeSummary)
    }

    val missingGroups = missingUpstreamGroups(desiredRootIntent.spec)
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
        job = converter.convertToJob(desiredRootIntent.spec, changeSummary),
        trigger = OrchestrationTrigger(intent.id)
      )
    ), changeSummary)
  }

  /**
   * Finds all intents that have a RuleSpec with a parent ID matching the root intent, and merges the intents into
   * the root intent for the duration of the convergence.
   */
  private fun mergeRootIntent(rootIntent: AmazonSecurityGroupIntent, thisIntent: AmazonSecurityGroupIntent): AmazonSecurityGroupIntent {
    val childRules = getChildRules(rootIntent.id)

    rootIntent.spec.inboundRules.addAll(childRules.map { it.spec.inboundRules }.flatten())
    if (thisIntent.spec is AmazonSecurityGroupRuleSpec) {
      rootIntent.spec.inboundRules.addAll(thisIntent.spec.inboundRules)
    }

    // TODO rz - outbound rules

    return rootIntent
  }

  private fun getRootIntent(intent: AmazonSecurityGroupIntent): AmazonSecurityGroupIntent? {
    val parentId = intent.parentId() ?: return intent

    val root = intentRepository.getIntent(parentId)
    if (root != null) {
      if (root !is AmazonSecurityGroupIntent) {
        throw DeclarativeException("Resolved root intent is not an AmazonSecurityGroupIntent: ${root.kind}")
      }
      return root
    }
    return null
  }

  private fun currentStateUpToDate(intentId: String,
                                   currentState: SecurityGroup?,
                                   desiredState: AmazonSecurityGroupSpec,
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

  private fun missingUpstreamGroups(spec: AmazonSecurityGroupSpec): List<String> {
    return spec.inboundRules
      .filterIsInstance<NamedReferenceSupport>()
      .filter {
        spec.name != it.name
      }
      .map {
        return@map if (loader.upstreamGroup(spec, it.name) == null) it.name else null
      }
      .filterNotNull()
      .distinct()
  }

  /**
   * TODO rz - Not happy with this. The intent repository needs to have a much better filter capability
   */
  private fun getChildRules(intentId: String): List<AmazonSecurityGroupIntent> =
    intentRepository.findByLabels(mapOf(PARENT_INTENT_LABEL to intentId))
      .filter { it.status == IntentStatus.ACTIVE }
      .filterIsInstance<AmazonSecurityGroupIntent>()
      .filter { it.spec is AmazonSecurityGroupRuleSpec }

}
