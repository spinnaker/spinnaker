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
package com.netflix.spinnaker.keel.intent.processor

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.ConvergeReason
import com.netflix.spinnaker.keel.ConvergeResult
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentProcessor
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.dryrun.ChangeType
import com.netflix.spinnaker.keel.intent.ANY_MAP_TYPE
import com.netflix.spinnaker.keel.intent.AmazonSecurityGroupSpec
import com.netflix.spinnaker.keel.intent.NamedReferenceSupport
import com.netflix.spinnaker.keel.intent.SecurityGroupIntent
import com.netflix.spinnaker.keel.intent.SecurityGroupSpec
import com.netflix.spinnaker.keel.intent.processor.converter.SecurityGroupConverter
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.Trigger
import com.netflix.spinnaker.keel.state.StateInspector
import com.netflix.spinnaker.keel.tracing.Trace
import com.netflix.spinnaker.keel.tracing.TraceRepository
import net.logstash.logback.argument.StructuredArguments.value
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

interface IntentProcessorStrategy {

  fun supports(intent: Intent<IntentSpec>): Boolean
}

interface SecurityGroupIntentProcessorStrategy<in T : SecurityGroupSpec> : IntentProcessorStrategy {

  fun getSecurityGroups(spec: T): Set<SecurityGroup>

  fun getMissingUpstreamSecurityGroups(spec: T): List<String>
}

@Component
class SecurityGroupIntentProcessor
@Autowired constructor(
  private val traceRepository: TraceRepository,
  private val cloudDriverService: CloudDriverService,
  private val clouddriverCache: CloudDriverCache,
  private val objectMapper: ObjectMapper,
  private val securityGroupConverter: SecurityGroupConverter
) : IntentProcessor<SecurityGroupIntent> {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun supports(intent: Intent<IntentSpec>) = intent is SecurityGroupIntent

  override fun converge(intent: SecurityGroupIntent): ConvergeResult {
    val changeSummary = ChangeSummary()

    log.info("Converging state for {}", value("intent", intent.id()))

    val currentState = getSecurityGroups(intent.spec)

    traceRepository.record(Trace(
      startingState = objectMapper.convertValue(mapOf("state" to currentState), ANY_MAP_TYPE),
      intent = intent
    ))

    if (currentStateUpToDate(intent.id(), currentState, intent.spec, changeSummary)) {
      changeSummary.addMessage(ConvergeReason.UNCHANGED.reason)
      return ConvergeResult(listOf(), changeSummary)
    }

    val missingGroups = missingUpstreamGroups(intent.spec)
    if (missingGroups.isNotEmpty()) {
      changeSummary.addMessage("Some upstream security groups are missing: $missingGroups")
      changeSummary.type = ChangeType.FAILED_PRECONDITIONS
      return ConvergeResult(listOf(),changeSummary)
    }

    changeSummary.type = if (currentState.isEmpty()) ChangeType.CREATE else ChangeType.UPDATE

    return ConvergeResult(listOf(
        OrchestrationRequest(
          name = "Upsert security group",
          application = intent.spec.application,
          description = "Converging on desired security group state",
          job = securityGroupConverter.convertToJob(intent.spec, changeSummary),
          trigger = Trigger(intent.id())
        )
      ),
      changeSummary
    )
  }

  private fun getSecurityGroups(spec: SecurityGroupSpec): Set<SecurityGroup> {
    if (spec is AmazonSecurityGroupSpec) {
      return spec.regions
        .map { region ->
          try {
            return@map if (spec.vpcName == null) {
              cloudDriverService.getSecurityGroup(spec.accountName, "aws", spec.name, region)
            } else {
              cloudDriverService.getSecurityGroup(spec.accountName, "aws", spec.name, region, clouddriverCache.networkBy(
                spec.vpcName,
                spec.accountName,
                region
              ).id)
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

  private fun currentStateUpToDate(intentId: String,
                                   currentState: Set<SecurityGroup>,
                                   desiredState: SecurityGroupSpec,
                                   changeSummary: ChangeSummary): Boolean {

    if (currentState.size != desiredState.regions.size) return false

    val desired = securityGroupConverter.convertToState(desiredState)

    val statePairs = mutableListOf<Pair<SecurityGroup, SecurityGroup>>()
    desired.forEach { d ->
      val key = "${d.accountName}/${d.region}/${d.name}"
      currentState.forEach { c ->
        if ("${c.accountName}/${c.region}/${c.name}" == key) {
          statePairs.add(Pair(c, d))
        }
      }
    }

    if (statePairs.size != desiredState.regions.size) return false

    val stateInspector = StateInspector(objectMapper)
    val diff = statePairs.flatMap {
      stateInspector.getDiff(
        intentId = intentId,
        currentState = it.first,
        desiredState = it.second,
        modelClass = SecurityGroup::class,
        specClass = SecurityGroupSpec::class,
        ignoreKeys = listOf("type", "id", "moniker", "summary")
      )
    }
    changeSummary.diff = diff
    return diff.isEmpty()
  }

  private fun missingUpstreamGroups(spec: SecurityGroupSpec): List<String> {
    return spec.inboundRules
      .filterIsInstance<NamedReferenceSupport>()
      .filter {
        spec.name != it.name
      }
      .flatMap {
        spec.regions.map { region ->
          if (spec is AmazonSecurityGroupSpec) {
            try {
              cloudDriverService.getSecurityGroup(
                spec.accountName,
                "aws",
                it.name,
                region,
                clouddriverCache.networkBy(spec.vpcName!!, spec.accountName, region).id
              )
            } catch (e: RetrofitError) {
              if (e.notFound()) {
                return@map it.name
              }
            }
          } else {
            log.error("${spec.javaClass.simpleName} is not supported yet")
          }
          return@map null
        }
      }
      .filterNotNull()
      .distinct()
  }
}
