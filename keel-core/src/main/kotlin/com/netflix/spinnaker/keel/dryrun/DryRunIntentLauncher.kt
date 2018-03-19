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
package com.netflix.spinnaker.keel.dryrun

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.*
import com.netflix.spinnaker.keel.event.BeforeIntentDryRunEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Plans, but does not apply, desired state intents.
 */
@Component(value = "dryRunIntentLauncher")
class DryRunIntentLauncher
@Autowired constructor(
  private val intentProcessors: List<IntentProcessor<*>>,
  private val registry: Registry,
  private val applicationEventPublisher: ApplicationEventPublisher
): IntentLauncher<DryRunLaunchedIntentResult> {

  private val invocationsId = registry.createId("intent.invocations", listOf(BasicTag("launcher", "dryRun")))

  override fun launch(intent: Intent<IntentSpec>): DryRunLaunchedIntentResult {
    registry.counter(invocationsId).increment()
    applicationEventPublisher.publishEvent(BeforeIntentDryRunEvent(intent))
    return intentProcessor(intentProcessors, intent).converge(intent).let {
      DryRunLaunchedIntentResult(summary = it.changeSummary)
    }
  }
}

data class DryRunLaunchedIntentResult(
  val summary: ChangeSummary
) : LaunchedIntentResult
