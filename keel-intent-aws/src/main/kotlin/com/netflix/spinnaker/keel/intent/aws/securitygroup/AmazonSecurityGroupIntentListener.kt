/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.keel.intent.aws.securitygroup

import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.IntentStatus
import com.netflix.spinnaker.keel.PARENT_INTENT_LABEL
import com.netflix.spinnaker.keel.attribute.EnabledAttribute
import com.netflix.spinnaker.keel.event.BeforeIntentDryRunEvent
import com.netflix.spinnaker.keel.event.BeforeIntentScheduleEvent
import com.netflix.spinnaker.keel.event.BeforeIntentUpsertEvent
import com.netflix.spinnaker.keel.event.IntentAwareEvent
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class AmazonSecurityGroupIntentListener(
  private val intentRepository: IntentRepository
) {

  private val log = LoggerFactory.getLogger(AmazonSecurityGroupIntentListener::class.java)

  /**
   * Adds an internal label on the intent linking security group rule intents
   * to their parent intent, even if the parent does not exist yet.
   */
  @EventListener(BeforeIntentUpsertEvent::class, BeforeIntentDryRunEvent::class)
  fun assignParentIntentLabel(event: IntentAwareEvent) {
    val intent = event.intent as? AmazonSecurityGroupIntent ?: return
    intent.parentId()
      ?.also { intent.labels[PARENT_INTENT_LABEL] = it }
  }

  /**
   * Due to how [AmazonSecurityGroupIntentProcessor] converges security groups
   * and individual rules, we shouldn't allow Security Group Rule Intents to be
   * converged on their own by the scheduler.
   *
   * This should only happen when Keel has an _active_ intent for the root SG.
   */
  @EventListener(BeforeIntentScheduleEvent::class)
  fun disableRuleIntentsOnScheduledConverge(event: BeforeIntentScheduleEvent) {
    val intent = event.intent as? AmazonSecurityGroupIntent ?: return
    if (event.intent.spec !is AmazonSecurityGroupRuleSpec) {
      return
    }

    val parentId = intent.parentId()
    if (parentId == null) {
      // This is bad, but we don't need to throw an exception or anything here.
      // Just process the intent. It's unnecessary overhead, but not the end
      // of the world.
      log.error("AmazonSecurityGroupIntent is a rule, but no parentId defined", kv("intentId", intent.id))
      return
    }

    val rootIntent = intentRepository.getIntent(parentId)
    if (rootIntent == null || rootIntent.status != IntentStatus.ACTIVE) {
      return
    }

    if (intent.hasAttribute(EnabledAttribute::class)) {
      intent.attributes.removeIf { it is EnabledAttribute }
    }
    intent.attributes.add(EnabledAttribute(false))
  }
}
