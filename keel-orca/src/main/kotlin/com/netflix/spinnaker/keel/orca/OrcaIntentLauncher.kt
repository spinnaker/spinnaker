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

import com.netflix.spinnaker.keel.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component(value = "orcaIntentLauncher")
class OrcaIntentLauncher
@Autowired constructor(
  private val intentProcessors: List<IntentProcessor<*>>,
  private val orcaService: OrcaService
) : IntentLauncher<OrcaLaunchedIntentResult> {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun launch(intent: Intent<IntentSpec>): OrcaLaunchedIntentResult? {
    val processor = intentProcessor(intentProcessors, intent)

    val tasks = processor.converge(intent)

    return OrcaLaunchedIntentResult(
      orchestrationIds = tasks.map {
        log.info("Launching orchestration for intent (kind: ${intent.kind})")
        orcaService.orchestrate(it).ref
      }
    )
  }
}

// TODO rz - Include orchestration ids?
class OrcaLaunchedIntentResult(
  val orchestrationIds: List<String>
) : LaunchedIntentResult
