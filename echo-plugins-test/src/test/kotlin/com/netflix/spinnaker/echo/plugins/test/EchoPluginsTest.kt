/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.echo.plugins.test

import com.netflix.spinnaker.echo.api.events.EventListener
import com.netflix.spinnaker.echo.api.events.NotificationAgent
import com.netflix.spinnaker.kork.plugins.tck.PluginsTck
import com.netflix.spinnaker.kork.plugins.tck.serviceFixture
import dev.minutest.rootContext
import strikt.api.expect
import strikt.assertions.isNotNull

class EchoPluginsTest : PluginsTck<EchoPluginsFixture>() {

  fun tests() = rootContext<EchoPluginsFixture> {
    context("an echo integration test environment and an echo plugin") {
      serviceFixture {
        EchoPluginsFixture()
      }

      defaultPluginTests()

      test("Event listener extension is loaded into context") {
        val eventListeners = applicationContext.getBeansOfType(EventListener::class.java)
        val extensionBeanName = "com.netflix.echo.enabled.plugin_eventListenerExtension"
        val extension = eventListeners[extensionBeanName]
        expect {
          that(extension).isNotNull()
        }
      }

      test("Notification agent extension is loaded into context") {
        val eventListeners = applicationContext.getBeansOfType(NotificationAgent::class.java)
        val extensionBeanName = "com.netflix.echo.enabled.plugin_notificationAgentExtension"
        val extension = eventListeners[extensionBeanName]
        expect {
          that(extension).isNotNull()
        }
      }
    }
  }
}
