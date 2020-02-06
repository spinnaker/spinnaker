/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.keel.model

import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.NotificationFrequency
import com.netflix.spinnaker.keel.api.NotificationFrequency.normal
import com.netflix.spinnaker.keel.api.NotificationFrequency.quiet
import com.netflix.spinnaker.keel.api.NotificationFrequency.verbose
import com.netflix.spinnaker.keel.model.NotificationEvent.ORCHESTRATION_COMPLETE
import com.netflix.spinnaker.keel.model.NotificationEvent.ORCHESTRATION_FAILED
import com.netflix.spinnaker.keel.model.NotificationEvent.ORCHESTRATION_STARTING

// This gets translated into an echo notification format in orca
data class OrcaNotification(
  val type: String,
  val address: String,
  val `when`: List<String>,
  val level: String = "pipeline",
  val message: Map<String, NotificationMessage>
)

enum class NotificationEvent {
  ORCHESTRATION_STARTING {
    override fun text(): String = "orchestration.starting"
    override fun notificationMessage(): NotificationMessage = NotificationMessage("$RAINBOW Managed update starting")
  },
  ORCHESTRATION_COMPLETE {
    override fun text(): String = "orchestration.complete"
    override fun notificationMessage(): NotificationMessage = NotificationMessage("$RAINBOW Managed update succeeded")
  },
  ORCHESTRATION_FAILED {
    override fun text(): String = "orchestration.failed"
    override fun notificationMessage(): NotificationMessage = NotificationMessage("$THUNDERCLOUD Managed update failed")
  };

  abstract fun text(): String
  abstract fun notificationMessage(): NotificationMessage
}

data class NotificationMessage(
  val text: String
)

const val RAINBOW = "\uD83C\uDF08"
const val THUNDERCLOUD = "\u26c8\ufe0f"

fun NotificationConfig.toOrcaNotification() =
  OrcaNotification(
    type = this.type.toString().toLowerCase(),
    address = this.address,
    `when` = translateFrequencyToEvents(frequency).map { it.text() },
    message = generateCustomMessages(frequency)
  )

fun translateFrequencyToEvents(frequency: NotificationFrequency): List<NotificationEvent> =
  when (frequency) {
    verbose -> listOf(ORCHESTRATION_FAILED, ORCHESTRATION_STARTING, ORCHESTRATION_COMPLETE)
    normal -> listOf(ORCHESTRATION_FAILED, ORCHESTRATION_COMPLETE)
    quiet -> listOf(ORCHESTRATION_FAILED)
  }

fun generateCustomMessages(frequency: NotificationFrequency): Map<String, NotificationMessage> =
  translateFrequencyToEvents(frequency).map { it.text() to it.notificationMessage() }.toMap()
