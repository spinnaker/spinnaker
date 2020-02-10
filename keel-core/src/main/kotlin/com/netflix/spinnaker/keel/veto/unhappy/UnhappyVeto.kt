/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.keel.veto.unhappy

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceStatus.UNHAPPY
import com.netflix.spinnaker.keel.persistence.UnhappyVetoRepository
import com.netflix.spinnaker.keel.veto.Veto
import com.netflix.spinnaker.keel.veto.VetoResponse
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * A veto that stops keel from checking a resource for a configurable
 * amount of time so that we don't flap on a resource forever.
 */
@Component
class UnhappyVeto(
  private val resourceRepository: ResourceRepository,
  private val diffFingerprintRepository: DiffFingerprintRepository,
  private val unhappyVetoRepository: UnhappyVetoRepository,
  private val dynamicConfigService: DynamicConfigService
) : Veto {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @Value("veto.unhappy.waiting-time")
  private var configuredWaitingTime: String = "PT10M"

  override fun check(resource: Resource<*>) =
    check(resource.id, resource.spec.application)

  override fun check(resourceId: String, application: String): VetoResponse {
    if (diffFingerprintRepository.diffCount(resourceId) <= maxDiffCount()) {
      // if we haven't generated the same diff 10 times, we should keep trying
      return allowedResponse()
    }

    val vetoStatus = unhappyVetoRepository.getVetoStatus(resourceId)
    if (vetoStatus.shouldSkip) {
      return deniedResponse(unhappyMessage())
    }

    // allow for a check every [waitingTime] even if the resource is unhappy
    if (vetoStatus.shouldRecheck) {
      unhappyVetoRepository.markUnhappyForWaitingTime(resourceId, application)
      return allowedResponse()
    }

    return if (resourceRepository.getStatus(resourceId) == UNHAPPY) {
      unhappyVetoRepository.markUnhappyForWaitingTime(resourceId, application)
      deniedResponse(unhappyMessage())
    } else {
      unhappyVetoRepository.markHappy(resourceId)
      allowedResponse()
    }
  }

  override fun messageFormat(): Map<String, Any> {
    TODO("not implemented")
  }

  override fun passMessage(message: Map<String, Any>) {
    TODO("not implemented")
  }

  override fun currentRejections(): List<String> =
    unhappyVetoRepository.getAll().toList()

  override fun currentRejectionsByApp(application: String) =
    unhappyVetoRepository.getAllForApp(application).toList()

  private fun maxDiffCount() =
    dynamicConfigService.getConfig(Int::class.java, "veto.unhappy.max-diff-count", 5)

  private fun waitingTime() =
    dynamicConfigService.getConfig(String::class.java, "veto.unhappy.waiting-time", configuredWaitingTime)

  private fun unhappyMessage(): String {
    val maxDiffs = maxDiffCount()
    val waitingTime = waitingTime()
    return "Resource is unhappy and our $maxDiffs actions have not fixed it. We will try again after $waitingTime, or if the diff changes."
  }
}
