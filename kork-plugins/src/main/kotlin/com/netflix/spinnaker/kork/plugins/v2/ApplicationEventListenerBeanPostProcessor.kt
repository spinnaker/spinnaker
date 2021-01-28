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

import com.netflix.spinnaker.kork.plugins.api.events.Async
import com.netflix.spinnaker.kork.plugins.api.events.SpinnakerEventListener
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.ApplicationListener
import org.springframework.core.annotation.AnnotationUtils

/**
 * Adapts [SpinnakerEventListener] instances to Spring's [ApplicationListener].
 *
 * This seems to be the least invasive method of registering application event listeners for plugins. It would've been
 * nice to use a facade `EventListener` annotation, but Spring's processing for this annotation is closed to the
 * modifications needed to make it work easily. The side effect of creating an adapter here is that plugin event
 * listeners will be invoked more often than they need to; deferring filtering of events to the adapter itself, rather
 * than using Spring to do this for us.
 */
class ApplicationEventListenerBeanPostProcessor : BeanPostProcessor {

  @Suppress("UNCHECKED_CAST")
  override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any? {
    if (SpinnakerEventListener::class.java.isAssignableFrom(bean.javaClass)) {
      return if (AnnotationUtils.findAnnotation(bean.javaClass, Async::class.java) == null) {
        SpringEventListenerAdapter(bean as SpinnakerEventListener<*>)
      } else {
        AsyncSpringEventListenerAdapter(bean as SpinnakerEventListener<*>)
      }
    }
    return super.postProcessBeforeInitialization(bean, beanName)
  }
}
