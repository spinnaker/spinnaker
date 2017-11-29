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
package com.netflix.spinnaker.keel.policy

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.netflix.spinnaker.keel.echo.EchoService
import com.netflix.spinnaker.keel.event.EventKind

private const val KIND = "Notification"

@JsonTypeName(KIND)
class NotificationPolicy
@JsonCreator constructor(
  spec: NotificationPolicySpec
) : Policy<NotificationPolicySpec>(
  kind = KIND,
  spec = spec
)

data class NotificationPolicySpec(
  val subscriptions: Map<EventKind, List<NotificationSpec>>
) : PolicySpec

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes(
  Type(EmailNotificationSpec::class),
  Type(HipChatNotificationSpec::class),
  Type(HipChatNotificationSpec::class),
  Type(PagerDutyNotificationSpec::class),
  Type(SlackNotificationSpec::class),
  Type(SmsNotificationSpec::class)
)
abstract class NotificationSpec {
  abstract val to: List<String>
  abstract val cc: List<String>
  abstract val severity: NotificationSeverity

  abstract val echoNotificationType: EchoService.Notification.Type

  @JsonIgnore
  abstract fun getAdditionalContext(): Map<String, Any?>
}

@JsonTypeName("email")
data class EmailNotificationSpec(
  override val to: List<String>,
  override val cc: List<String> = listOf(),
  override val severity: NotificationSeverity = NotificationSeverity.NORMAL,
  val subject: String,
  val body: String
) : NotificationSpec() {

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
) : NotificationSpec(){

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
) : NotificationSpec() {

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
) : NotificationSpec(){

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
) : NotificationSpec(){

  @JsonIgnore
  override val echoNotificationType = EchoService.Notification.Type.SMS

  override fun getAdditionalContext() = mapOf("body" to body)
}

enum class NotificationSeverity {
  NORMAL,
  HIGH
}
