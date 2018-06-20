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
import com.netflix.spinnaker.keel.event.AssetAwareEvent
import com.netflix.spinnaker.keel.event.EventKind
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

  @EventListener(AssetAwareEvent::class)
  fun onAssetAwareEvent(event: AssetAwareEvent) {
    event.asset.getAttribute(NotificationAttribute::class)
      ?.also { attribute ->
        attribute.value.subscriptions
          .filter { it.key == event.kind }
          .forEach { sub ->
            log.info("Sending {} notifications for {}", value("kind", sub.key), value("assetId", event.asset.id()))
            sub.value.forEach { sendNotification(event.asset.id(), event.kind, it) }
          }
      }
  }

  private fun sendNotification(assetId: String, eventKind: EventKind, notification: NotificationSpec) {
    try {
      echoService.create(EchoService.Notification(
        notificationType = notification.echoNotificationType,
        to = notification.to,
        cc = notification.cc,
        // TODO rz - Support other, non-generic templates
        templateGroup = "keelAsset",
        severity = notification.severity,
        source = EchoService.Notification.Source("keel"),
        additionalContext = notification.getAdditionalContext().toMutableMap().let {
          it.putAll(mapOf(
            "eventKind" to eventKind.toValue(),
            "assetId" to assetId
          ))
          it
        }
      ))
      registry.counter(notificationsId.withTag("result", "success"))
    } catch (e: Exception) {
      log.error("Failed sending {} notification for {}", value("kind", eventKind), value("assetId", assetId), e)
      registry.counter(notificationsId.withTag("result", "failed"))
    }
  }
}
