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
package com.netflix.spinnaker.keel.echo

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.event.IntentAwareEvent
import com.netflix.spinnaker.keel.policy.NotificationPolicy
import net.logstash.logback.argument.StructuredArguments.value
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class EventNotificationListener(
  private val echoService: EchoService,
  private val registry: Registry
) {

  private val log = LoggerFactory.getLogger(javaClass)

  private val notificationsId = registry.createId("echo.notifications")

  @EventListener(IntentAwareEvent::class)
  fun onIntentAwareEvent(event: IntentAwareEvent) {
    event.intent.getPolicy(NotificationPolicy::class)
      ?.also { policy ->
        policy.spec.subscriptions
          .filter { it.key == event.kind }
          .forEach { sub ->
            log.info(
              "Sending {} notifications for intent (intent: {})",
              value("kind", sub.key),
              value("intent", event.intent.id)
            )

            sub.value.forEach { notification ->
              try {
                echoService.create(EchoService.Notification(
                  notificationType = notification.echoNotificationType,
                  to = notification.to,
                  cc = notification.cc,
                  // TODO rz - Support other, non-generic templates
                  templateGroup = "keelIntent",
                  severity = notification.severity,
                  source = EchoService.Notification.Source("keel"),
                  additionalContext = notification.getAdditionalContext().toMutableMap().let {
                    it.putAll(mapOf(
                      "eventKind" to event.kind.toValue(),
                      "intentId" to event.intent.id
                    ))
                    it
                  }
                ))
                registry.counter(notificationsId.withTag("result", "success"))
              } catch (cause: Exception) {
                log.error(
                  "Failed creating notification for intent (intent: {}, type: {})",
                  value("intent", event.intent.id),
                  value("kind", sub.key),
                  cause
                )
                registry.counter(notificationsId.withTag("result", "failed"))
              }
            }
          }
      }
  }
}
