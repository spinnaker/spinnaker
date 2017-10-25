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
package com.netflix.spinnaker.keel.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.IntentStatus
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class RedisIntentRepository
@Autowired constructor(
  private val mainRedisClientDelegate: RedisClientDelegate,
  private val previousRedisClientDelegate: RedisClientDelegate?,
  private val objectMapper: ObjectMapper
) : IntentRepository {

  override fun upsertIntent(intent: Intent<IntentSpec>) {
    mainRedisClientDelegate.withCommandsClient { c ->
      c.set(intentKey(intent.getId()), objectMapper.writeValueAsString(intent))
    }
  }

  override fun getIntents(): List<Intent<IntentSpec>> {
    throw UnsupportedOperationException("not implemented")
  }

  override fun getIntents(statuses: List<IntentStatus>): List<Intent<IntentSpec>> {
    throw UnsupportedOperationException("not implemented")
  }

  override fun getIntent(id: String): Intent<IntentSpec>? {
    throw UnsupportedOperationException("not implemented")
  }
}

internal fun intentKey(intentId: String) = "intents:$intentId"
