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

package com.netflix.spinnaker.orca.discovery

import com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import org.slf4j.Logger
import org.springframework.context.ApplicationListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A component that starts doing something when the instance is up in discovery
 * and stops doing that thing when it goes down.
 */
interface DiscoveryActivated : ApplicationListener<RemoteStatusChangedEvent> {

  override fun onApplicationEvent(event: RemoteStatusChangedEvent) =
    event.source.let { e ->
      if (e.status == UP) {
        log.info("Instance is ${e.status}... ${javaClass.simpleName} starting")
        enable()
      } else if (e.previousStatus == UP) {
        log.info("Instance is ${e.status}... ${javaClass.simpleName} stopping")
        disable()
      }
    }

  private fun enable() = enabled.set(true)

  private fun disable() = enabled.set(false)

  fun ifEnabled(block: () -> Unit) {
    if (enabled.get()) {
      block.invoke()
    }
  }

  val log: Logger
  val enabled: AtomicBoolean
}
