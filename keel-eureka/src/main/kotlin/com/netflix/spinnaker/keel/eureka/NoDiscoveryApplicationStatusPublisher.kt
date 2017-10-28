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
package com.netflix.spinnaker.keel.eureka

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.StatusChangeEvent
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent

class NoDiscoveryApplicationStatusPublisher(
  private val publisher: ApplicationEventPublisher
) : ApplicationListener<ContextRefreshedEvent> {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun onApplicationEvent(event: ContextRefreshedEvent) {
    log.warn("No discovery client is available, assuming application is up")
    setInstanceStatus(InstanceInfo.InstanceStatus.UP)
  }

  private fun setInstanceStatus(current: InstanceInfo.InstanceStatus) {
    val previous = instanceStatus
    instanceStatus = current
    publisher.publishEvent(RemoteStatusChangedEvent(StatusChangeEvent(previous, current)))
  }

  fun setInstanceEnabled(enabled: Boolean) {
    setInstanceStatus(if (enabled) InstanceInfo.InstanceStatus.UP else InstanceInfo.InstanceStatus.OUT_OF_SERVICE)
  }

  companion object {

    private var instanceStatus = InstanceInfo.InstanceStatus.UNKNOWN
  }
}
