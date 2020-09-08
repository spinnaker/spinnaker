/*
 * Copyright 2017 Netflix, Inc.
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
 */

package com.netflix.spinnaker.q.discovery

import com.netflix.spinnaker.kork.discovery.InstanceStatus.UP
import com.netflix.spinnaker.kork.discovery.RemoteStatusChangedEvent
import com.netflix.spinnaker.q.Activator
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

/**
 * An [Activator] implementation that responds to status change events from Eureka.
 */
@Component
class DiscoveryActivator : Activator, ApplicationListener<RemoteStatusChangedEvent> {

  private val _enabled = AtomicBoolean()

  override val enabled: Boolean
    get() = _enabled.get()

  override fun onApplicationEvent(event: RemoteStatusChangedEvent) =
    event.source.let { e ->
      if (e.status == UP) {
        log.info("Instance is ${e.status}... ${javaClass.simpleName} starting")
        _enabled.set(true)
      } else if (e.previousStatus == UP) {
        log.info("Instance is ${e.status}... ${javaClass.simpleName} stopping")
        _enabled.set(false)
      }
    }

  private val log = LoggerFactory.getLogger(javaClass)
}
