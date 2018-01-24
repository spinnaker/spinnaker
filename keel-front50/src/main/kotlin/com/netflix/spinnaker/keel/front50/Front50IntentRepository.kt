/*
 * Copyright 2017 Netflix, Inc.
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
 */

package com.netflix.spinnaker.keel.front50

import com.google.common.util.concurrent.RateLimiter
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.Front50RateLimitProperties
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.IntentStatus
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnExpression("\${front50.intentRepositoryEnabled:true}")
class Front50IntentRepository(
  private val front50Service: Front50Service,
  private val registry: Registry,
  front50RateLimitProperties: Front50RateLimitProperties
): IntentRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  private val rateLimitTimeId = registry.createId("front50.rateLimitTimeSeconds")

  private val rateLimiter = RateLimiter.create(front50RateLimitProperties.requestsPerSecond)

  init {
    log.info("Using ${javaClass.simpleName}")
  }

  override fun upsertIntent(intent: Intent<IntentSpec>) =
    rateLimited { front50Service.upsertIntent(intent) }

  override fun getIntents() = rateLimited { front50Service.getIntents() }

  override fun getIntents(status: List<IntentStatus>) = rateLimited { front50Service.getIntentsByStatus(status) }

  override fun getIntent(id: String) = rateLimited { front50Service.getIntent(id) }

  @Suppress("IMPLICIT_CAST_TO_ANY")
  override fun deleteIntent(id: String, preserveHistory: Boolean) {
    rateLimited {
      if (preserveHistory) {
        getIntent(id).also {
          it.status = IntentStatus.DELETED
          upsertIntent(it)
        }
      } else {
        front50Service.deleteIntent(id)
      }
    }
  }

  private inline fun <reified T> rateLimited(op: () -> T): T =
    rateLimiter.acquire().let { secondsBlocked ->
      registry.timer(rateLimitTimeId).record((secondsBlocked * 1000).toLong(), TimeUnit.MILLISECONDS)
      op.invoke()
    }
}
