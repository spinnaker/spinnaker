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

import com.netflix.spinnaker.keel.PARENT_INTENT_LABEL
import com.netflix.spinnaker.keel.event.BeforeIntentDryRunEvent
import com.netflix.spinnaker.keel.event.BeforeIntentUpsertEvent
import com.netflix.spinnaker.keel.event.IntentAwareEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Adds an internal label on the intent linking security group rule intents
 * to their parent intent, even if the parent does not exist yet.
 */
@Component
class AmazonSecurityGroupIntentListener {

  @EventListener(BeforeIntentUpsertEvent::class, BeforeIntentDryRunEvent::class)
  fun assignParentIntentLabel(event: IntentAwareEvent) {
    val intent = event.intent as? AmazonSecurityGroupIntent ?: return
    intent.parentId()
      ?.also { intent.labels[PARENT_INTENT_LABEL] = it }
  }
}
