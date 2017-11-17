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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.IntentStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class Front50IntentRepository
@Autowired constructor(
    private val front50Service: Front50Service,
    private val objectMapper: ObjectMapper
): IntentRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  @PostConstruct
  fun init() {
    log.info("Using ${javaClass.simpleName}")
  }

  override fun upsertIntent(intent: Intent<IntentSpec>) = front50Service.upsertIntent(intent)

  override fun getIntents() = front50Service.getIntents()

  override fun getIntents(status: List<IntentStatus>) = front50Service.getIntentsByStatus(status)

  override fun getIntent(id: String) = front50Service.getIntent(id)

  override fun deleteIntent(id: String) {
    front50Service.deleteIntent(id)
  }
}
