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
import com.netflix.spinnaker.keel.*
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.TimeUnit

@Component(value = "orcaIntentLauncher")
open class OrcaIntentLauncher
@Autowired constructor(
  private val intentProcessors: List<IntentProcessor<*>>,
  private val orcaService: OrcaService,
  private val registry: Registry,
  private val applicationEventPublisher: ApplicationEventPublisher,
  private val intentActivityRepository: IntentActivityRepository,
  private val clock: Clock
) : IntentLauncher<OrcaLaunchedIntentResult> {

  private val log = LoggerFactory.getLogger(javaClass)
  private val invocationsId = registry.createId("intent.invocations", listOf(BasicTag("launcher", "orca")))
  private val invocationTimeId = registry.createId("intent.invocationTime", listOf(BasicTag("launcher", "orca")))

  override fun launch(intent: Intent<IntentSpec>) =
    recordedTime(intent) {
      val result = intentProcessor(intentProcessors, intent).converge(intent)

      if (result.orchestrations.isNotEmpty()) {
        applicationEventPublisher.publishEvent(ConvergenceRequiredEvent(intent))
      }

      val orchestrationIds = result.orchestrations.map {
        orcaService.orchestrate(it).ref
      }

      intentActivityRepository.logConvergence(IntentConvergenceRecord(
        intentId = intent.id,
        changeType = result.changeSummary.type,
        orchestrations = orchestrationIds,
        messages = result.changeSummary.message,
        diff = result.changeSummary.diff,
        actor = "keel:scheduledConvergence",
        timestampMillis = clock.millis()
      ))

      registry.counter(invocationsId
        .withTags(intent.getMetricTags())
        .withTag("change", result.changeSummary.type.toString())
      ).increment()

      OrcaLaunchedIntentResult(orchestrationIds, result.changeSummary)
    }

  private fun recordedTime(intent: Intent<IntentSpec>, callable: () -> OrcaLaunchedIntentResult): OrcaLaunchedIntentResult {
    val start = registry.clock().monotonicTime()
    return callable.invoke()
      .also {
        registry.timer(invocationTimeId.withTags(intent.getResultTags(it))).record(
          registry.clock().monotonicTime() - start,
          TimeUnit.NANOSECONDS
        )
      }
  }

  private fun Intent<IntentSpec>.getResultTags(result: OrcaLaunchedIntentResult) =
    getMetricTags().toMutableList().apply {
      add(BasicTag("change", result.changeSummary.type.toString()))
    }
}

data class OrcaLaunchedIntentResult(
  val orchestrationIds: List<String>,
  val changeSummary: ChangeSummary
) : LaunchedIntentResult
