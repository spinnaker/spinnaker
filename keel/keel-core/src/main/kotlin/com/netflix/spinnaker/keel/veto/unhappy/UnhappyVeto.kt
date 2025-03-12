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

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.patterns.PolledMeter
import com.netflix.spinnaker.config.UnhappyVetoConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.UnhappyVetoRepository
import com.netflix.spinnaker.keel.veto.Veto
import com.netflix.spinnaker.keel.veto.VetoResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * A veto that stops keel from trying to fix the same diff over and over again.
 *
 * We try a set number of times to resolve a diff, spaced a configurable time apart.
 */
@Component
@EnableConfigurationProperties(UnhappyVetoConfig::class)
final class UnhappyVeto(
  private val diffFingerprintRepository: DiffFingerprintRepository,
  private val unhappyVetoRepository: UnhappyVetoRepository,
  private val resourceRepository: ResourceRepository,
  private val springEnv: Environment,
  private val config: UnhappyVetoConfig,
  private val spectator: Registry,
  private val clock: Clock
) : Veto {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val NUM_VETOS_GAUGE = "keel.vetos.unhappy.num"

  init {
    PolledMeter
      .using(spectator)
      .withName(NUM_VETOS_GAUGE)
      .monitorValue(this) { it.numberRejections().toDouble() }
  }

  private val maxRetries: Int
    get() = springEnv.getProperty("keel.unhappy.maxRetries", Int::class.java, config.maxRetries)

  private val timeBetweenRetries: Duration
    get() = springEnv.getProperty("keel.unhappy.timeBetweenRetries", Duration::class.java, config.timeBetweenRetries)

  fun clearVeto(resourceId: String) {
    val resource = resourceRepository.get(resourceId)
    unhappyVetoRepository.delete(resource)
    diffFingerprintRepository.clear(resourceId)
  }

  override suspend fun check(resource: Resource<*>): VetoResponse {
    val numActionsTaken = diffFingerprintRepository.actionTakenCount(resource.id)
    return when {
      numActionsTaken == 0 -> {
        // no actions taken, so allow the check
        unhappyVetoRepository.delete(resource)
        allowedResponse()
      }
      numActionsTaken < maxRetries -> {
        var recheckTime = unhappyVetoRepository.getRecheckTime(resource)
        when {
          recheckTime == null -> {
            // this is the first time we're seeing this resource as unhappy, mark as such and deny
            recheckTime = clock.instant() + timeBetweenRetries
            unhappyVetoRepository.markUnhappy(resource, recheckTime)
            deniedResponse(
              message = denyWaitingMessage(numActionsTaken, recheckTime),
              vetoArtifact = false
            )
          }
          recheckTime > clock.instant() -> {
            // we know this resource is unhappy and we can't recheck it yet
            deniedResponse(
              message = denyWaitingMessage(numActionsTaken, recheckTime),
              vetoArtifact = false
            )
          }
          else -> {
            // resource is unhappy but we can recheck it
            // allow one check and update the new recheck time
            unhappyVetoRepository.markUnhappy(resource, clock.instant() + timeBetweenRetries)
            allowedResponse()
          }
        }
      }
      else -> {
        // more than 0 actions taken, we probably can't fix this diff, deny forever
        unhappyVetoRepository.markUnhappy(resource, null)
        deniedResponse(message = denyForeverMessage(), vetoArtifact = false)
      }
    }
  }

  fun numberRejections(): Int =
    unhappyVetoRepository.getNumberOfRejections()

  override fun currentRejections(): List<String> =
    unhappyVetoRepository.getAll().toList()

  override fun currentRejectionsByApp(application: String) =
    unhappyVetoRepository.getAllForApp(application).toList()

  private fun denyWaitingMessage(numTries: Int, retryTime: Instant): String =
    "Resource is unhappy and our $numTries action(s) have not fixed it yet." +
      " We will try again in ${Duration.between(clock.instant(), retryTime).toMinutes()} minutes."

  private fun denyForeverMessage(): String =
    "Resource is unhappy and our $maxRetries action(s) did not fix it." +
      " We will not take automatic action until the diff changes."
}
