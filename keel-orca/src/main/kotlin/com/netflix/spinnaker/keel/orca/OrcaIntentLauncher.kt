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
package com.netflix.spinnaker.keel.orca

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentLauncher
import com.netflix.spinnaker.keel.IntentProcessor
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.LaunchedIntentResult
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import net.logstash.logback.argument.StructuredArguments.value
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component(value = "orcaIntentLauncher")
open class OrcaIntentLauncher
@Autowired constructor(
  private val intentProcessors: List<IntentProcessor<*>>,
  private val orcaService: OrcaService,
  private val registry: Registry,
  private val applicationEventPublisher: ApplicationEventPublisher
) : IntentLauncher<OrcaLaunchedIntentResult> {

  private val log = LoggerFactory.getLogger(javaClass)
  private val invocationsId = registry.createId("intent.invocations", listOf(BasicTag("launcher", "orca")))
  private val invocationTimeId = registry.createId("intent.invocationTime", listOf(BasicTag("launcher", "orca")))

  override fun launch(intent: Intent<IntentSpec>): OrcaLaunchedIntentResult {
    registry.counter(invocationsId.withTags(intent.getMetricTags())).increment()

    return registry.timer(invocationTimeId.withTags(intent.getMetricTags())).record<OrcaLaunchedIntentResult> {
      val result = intentProcessor(intentProcessors, intent).converge(intent)

      if (result.orchestrations.isEmpty()) {
        log.info("Not converging state for intent {}, {}",
          value("intent", intent.id()),
          value("summary", result.changeSummary))
      } else {
        applicationEventPublisher.publishEvent(ConvergenceRequiredEvent(intent))
      }

      val orchestrationIds = result.orchestrations.map {
        log.info("Launching orchestration for intent {}, {}",
          value("intent", intent.id()),
          value("summary", result.changeSummary))
        orcaService.orchestrate(it).ref
      }

      if (orchestrationIds.isNotEmpty()) {
        log.info(
          "Launched orchestrations for intent: {} (tasks: {})",
          value("intent", intent.id()),
          value("tasks", orchestrationIds)
        )
      }
      OrcaLaunchedIntentResult(orchestrationIds, result.changeSummary)
    }
  }
}

data class OrcaLaunchedIntentResult(
  val orchestrationIds: List<String>,
  val changeSummary: ChangeSummary
) : LaunchedIntentResult
