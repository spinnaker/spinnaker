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
package com.netflix.spinnaker.keel.echo

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.keel.attribute.Attribute
import com.netflix.spinnaker.keel.event.EventKind

@JsonTypeName("Notification")
class NotificationAttribute(value: NotificationSubscriptions) : Attribute<NotificationSubscriptions>("Notification", value)

data class NotificationSubscriptions(
  val subscriptions: Map<EventKind, List<NotificationSpec>>
)

enum class NotificationSeverity {
  NORMAL,
  HIGH
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes(
  JsonSubTypes.Type(EmailNotificationSpec::class),
  JsonSubTypes.Type(HipChatNotificationSpec::class),
  JsonSubTypes.Type(HipChatNotificationSpec::class),
  JsonSubTypes.Type(PagerDutyNotificationSpec::class),
  JsonSubTypes.Type(SlackNotificationSpec::class),
  JsonSubTypes.Type(SmsNotificationSpec::class)
)
interface NotificationSpec {
  val to: List<String>
  val cc: List<String>
  val severity: NotificationSeverity

  val echoNotificationType: EchoService.Notification.Type

  @JsonIgnore
  fun getAdditionalContext(): Map<String, Any?>
}

@JsonTypeName("email")
data class EmailNotificationSpec(
  override val to: List<String>,
  override val cc: List<String> = listOf(),
  override val severity: NotificationSeverity = NotificationSeverity.NORMAL,
  val subject: String,
  val body: String
) : NotificationSpec {

  @JsonIgnore
  override val echoNotificationType = EchoService.Notification.Type.EMAIL

  override fun getAdditionalContext() = mapOf(
    "subject" to subject,
    "body" to body
  )
}

@JsonTypeName("hipchat")
data class HipChatNotificationSpec(
  override val to: List<String>,
  override val cc: List<String> = listOf(),
  override val severity: NotificationSeverity = NotificationSeverity.NORMAL,
  val message: String,
  val color: String,
  val notify: Boolean
) : NotificationSpec {

  @JsonIgnore
  override val echoNotificationType = EchoService.Notification.Type.HIPCHAT

  override fun getAdditionalContext() = mapOf(
    "message" to message,
    "color" to color,
    "notify" to notify
  )
}

@JsonTypeName("pagerduty")
data class PagerDutyNotificationSpec(
  override val to: List<String>,
  override val cc: List<String> = listOf(),
  override val severity: NotificationSeverity = NotificationSeverity.NORMAL,
  val message: String
) : NotificationSpec {

  @JsonIgnore
  override val echoNotificationType = EchoService.Notification.Type.PAGER_DUTY

  override fun getAdditionalContext() = mapOf("message" to message)
}

@JsonTypeName("slack")
data class SlackNotificationSpec(
  override val to: List<String>,
  override val cc: List<String> = listOf(),
  override val severity: NotificationSeverity = NotificationSeverity.NORMAL,
  val message: String,
  val color: String
) : NotificationSpec {

  @JsonIgnore
  override val echoNotificationType = EchoService.Notification.Type.SLACK

  override fun getAdditionalContext() = mapOf(
    "message" to message,
    "color" to color
  )
}

@JsonTypeName("sms")
data class SmsNotificationSpec(
  override val to: List<String>,
  override val cc: List<String> = listOf(),
  override val severity: NotificationSeverity = NotificationSeverity.NORMAL,
  val body: String
) : NotificationSpec {

  @JsonIgnore
  override val echoNotificationType = EchoService.Notification.Type.SMS

  override fun getAdditionalContext() = mapOf("body" to body)
}
