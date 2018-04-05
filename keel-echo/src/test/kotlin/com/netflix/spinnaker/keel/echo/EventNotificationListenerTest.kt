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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.keel.event.AfterIntentUpsertEvent
import com.netflix.spinnaker.keel.event.EventKind
import com.netflix.spinnaker.keel.test.GenericTestIntentSpec
import com.netflix.spinnaker.keel.test.TestIntent
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

object EventNotificationListenerTest {

  val echoService = mock<EchoService>()
  val registry = NoopRegistry()

  val subject = EventNotificationListener(echoService, registry)

  @AfterEach
  fun cleanup() {
    reset(echoService)
  }

  @Test
  fun `should send notifications for each subscription`() {
    val event = AfterIntentUpsertEvent(
      TestIntent(
        GenericTestIntentSpec("test:1", mapOf()),
        attributes = mutableListOf(
          NotificationAttribute(NotificationSubscriptions(
            subscriptions = mapOf(
              EventKind.AFTER_INTENT_UPSERT to listOf(
                EmailNotificationSpec(
                  to = listOf("example@example.com"),
                  subject = "Intent was upserted",
                  body = "test:1 was upserted and you like to know about that"
                ),
                SlackNotificationSpec(
                  to = listOf("my-channel"),
                  message = "test:1 was upserted",
                  color = "#ff0000"
                )
              )
            )
          ))
        )
      )
    )

    subject.onIntentAwareEvent(event)

    verify(echoService).create(EchoService.Notification(
      notificationType = EchoService.Notification.Type.EMAIL,
      to = listOf("example@example.com"),
      cc = listOf(),
      templateGroup = "keelIntent",
      severity = NotificationSeverity.NORMAL,
      source = EchoService.Notification.Source("keel"),
      additionalContext = mapOf(
        "eventKind" to "afterIntentUpsert",
        "intentId" to "test:test:1",
        "subject" to "Intent was upserted",
        "body" to "test:1 was upserted and you like to know about that"
      )
    ))

    verify(echoService).create(EchoService.Notification(
      notificationType = EchoService.Notification.Type.SLACK,
      to = listOf("my-channel"),
      cc = listOf(),
      templateGroup = "keelIntent",
      severity = NotificationSeverity.NORMAL,
      source = EchoService.Notification.Source("keel"),
      additionalContext = mapOf(
        "eventKind" to "afterIntentUpsert",
        "intentId" to "test:test:1",
        "message" to "test:1 was upserted",
        "color" to "#ff0000"
      )
    ))
  }
}
