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
 *
 */
package com.netflix.spinnaker.kork.plugins.v2

import com.netflix.spinnaker.kork.plugins.api.events.SpinnakerEventListener
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.scheduling.annotation.Async

/**
 * Creates an asynchronous [ApplicationListener], delegating all implementation to [SpringEventListenerAdapter].
 */
class AsyncSpringEventListenerAdapter(
  eventListener: SpinnakerEventListener<*>
) : ApplicationListener<ApplicationEvent> {

  private val adapter = SpringEventListenerAdapter(eventListener)

  @Async
  override fun onApplicationEvent(event: ApplicationEvent) {
    adapter.onApplicationEvent(event)
  }
}
