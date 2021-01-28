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
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.ResolvableType
import org.springframework.util.ReflectionUtils

/**
 * Adapts Spring [ApplicationEvent] events to the plugin-friendly [SpinnakerApplicationEvent] event type.
 */
class SpringEventListenerAdapter(
  internal val eventListener: SpinnakerEventListener<*>
) : ApplicationListener<ApplicationEvent> {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val interestedType: Class<*>? = findEventType(eventListener)

  override fun onApplicationEvent(event: ApplicationEvent) {
    if (interestedType != null && interestedType.isAssignableFrom(event.javaClass)) {
      // Grossssssssss
      val method = eventListener.javaClass.getMethod("onApplicationEvent", interestedType)
      method.isAccessible = true
      ReflectionUtils.invokeMethod(method, eventListener, event)
    }
  }

  /**
   * Finds the event type for a [SpinnakerEventListener] class.
   *
   * It's not enough to just look at the immediate interfaces of the type, since it's possible a plugin developer
   * will make an abstract class for all event listeners.
   */
  @Suppress("TooGenericExceptionCaught")
  private fun findEventType(eventListener: SpinnakerEventListener<*>): Class<*>? {
    return try {
      val eventListenerType = ResolvableType.forInstance(eventListener)
      findEventType(eventListenerType)
    } catch (e: Exception) {
      log.warn(
        "Unable to resolve event type for listener, all events will be ignored: {}",
        eventListener.extensionClass.simpleName
      )
      return null
    }
  }

  private fun findEventType(type: ResolvableType): Class<*>? {
    if (type.superType.rawClass.name != "java.lang.Object") {
      return findEventType(type.superType)
    }

    return type.interfaces
      .firstOrNull { SpinnakerEventListener::class.java.isAssignableFrom(it.rawClass!!) }
      ?.getGeneric(0)
      ?.rawClass
  }
}
