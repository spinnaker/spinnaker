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

import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentLauncher
import com.netflix.spinnaker.keel.IntentProcessor
import com.netflix.spinnaker.keel.IntentSpec
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier("orcaIntentLauncher")
class OrcaIntentLauncher
@Autowired constructor(
  private val intentProcessors: List<IntentProcessor<*>>,
  private val orcaService: OrcaService
) : IntentLauncher {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun launch(intent: Intent<IntentSpec>) {
    val processor = intentProcessor(intentProcessors, intent)

    val tasks = processor.converge(intent)

    tasks.forEach {
      log.info("Launching orchestration for intent (kind: ${intent.kind})")

      // TODO rz - associate orchestration with intent
      orcaService.orchestrate(it)
    }
  }
}
