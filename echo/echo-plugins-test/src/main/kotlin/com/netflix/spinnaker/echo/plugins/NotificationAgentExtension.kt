/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.echo.plugins

import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.api.events.NotificationAgent
import com.netflix.spinnaker.echo.api.events.NotificationParameter
import org.pf4j.Extension

@Extension
class NotificationAgentExtension : NotificationAgent {
  override fun getNotificationType() = "extension_notification"
  override fun sendNotifications(notification: MutableMap<String, Any>, application: String, event: Event, status: String) {}
  override fun getParameters() = emptyList<NotificationParameter>()
}
