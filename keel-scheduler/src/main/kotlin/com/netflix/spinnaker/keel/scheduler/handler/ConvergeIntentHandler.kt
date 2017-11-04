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
package com.netflix.spinnaker.keel.scheduler.handler

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentActivityRepository
import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.orca.OrcaIntentLauncher
import com.netflix.spinnaker.keel.scheduler.ConvergeIntent
import com.netflix.spinnaker.q.MessageHandler
import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock

private const val CANCELLATION_REASON_NOT_FOUND = "notFound"
private const val CANCELLATION_REASON_TIMEOUT = "timeout"

@Component
class ConvergeIntentHandler
@Autowired constructor(
  override val queue: Queue,
  private val intentRepository: IntentRepository,
  private val intentActivityRepository: IntentActivityRepository,
  private val orcaIntentLauncher: OrcaIntentLauncher,
  private val clock: Clock,
  private val registry: Registry
) : MessageHandler<ConvergeIntent> {

  private val log = LoggerFactory.getLogger(javaClass)

  private val invocationsId = registry.createId("intent.invocations")
  private val canceledId = registry.createId("intent.cancellations")

  override fun handle(message: ConvergeIntent) {
    log.info("Converging intent ${message.intent.getId()}")

    if (clock.millis() > message.timeoutTtl) {
      log.warn("Intent timed out, canceling converge: ${message.intent.getId()}")
      registry.counter(canceledId.withTags("kind", message.intent.kind, "reason", CANCELLATION_REASON_TIMEOUT))
      return
    }

    val intent = getIntent(message)
    if (intent == null) {
      log.warn("Intent no longer exists, canceling converge: ${message.intent.getId()}")
      registry.counter(canceledId.withTags("kind", message.intent.kind, "reason", CANCELLATION_REASON_NOT_FOUND))
      return
    }

    try {
      orcaIntentLauncher.launch(intent)
        .takeIf { it.orchestrationIds.isNotEmpty() }
        ?.also { result ->
          intentActivityRepository.addOrchestrations(intent.getId(), result.orchestrationIds)
        }
      registry.counter(invocationsId.withTags("kind", message.intent.kind, "schema", message.intent.schema, "result", "success"))
    } catch (t: Throwable) {
      log.error("Failed launching intent: ${intent.getId()}", t)
      registry.counter(invocationsId.withTags("kind", message.intent.kind, "schema", message.intent.schema, "result", "failed"))
    }
  }

  private fun getIntent(message: ConvergeIntent): Intent<IntentSpec>? {
    if (clock.millis() <= message.stalenessTtl) {
      return message.intent
    }

    log.info("Refreshing intent state for ${message.intent.getId()}")
    return intentRepository.getIntent(message.intent.getId())
  }

  override val messageType = ConvergeIntent::class.java
}
