/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.listeners

import com.netflix.spinnaker.orca.annotations.Sync
import org.springframework.beans.factory.BeanClassLoaderAware
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ApplicationEventMulticaster
import org.springframework.core.ResolvableType
import java.util.function.Predicate

/**
 * Supports sync & async event listeners. Listeners are treated as asynchronous unless
 * explicitly marked as synchronous via the {@code Sync} annotation.
 */
class DelegatingApplicationEventMulticaster(
  private val syncApplicationEventMulticaster: ApplicationEventMulticaster,
  private val asyncApplicationEventMulticaster: ApplicationEventMulticaster
) : ApplicationEventMulticaster, BeanFactoryAware, BeanClassLoaderAware {

  override fun multicastEvent(event: ApplicationEvent) {
    asyncApplicationEventMulticaster.multicastEvent(event)
    syncApplicationEventMulticaster.multicastEvent(event)
  }

  override fun multicastEvent(event: ApplicationEvent, eventType: ResolvableType?) {
    asyncApplicationEventMulticaster.multicastEvent(event, eventType)
    syncApplicationEventMulticaster.multicastEvent(event, eventType)
  }

  override fun addApplicationListener(listener: ApplicationListener<*>) {
    if (isSynchronous(listener)) {
      syncApplicationEventMulticaster.addApplicationListener(listener)
    } else {
      asyncApplicationEventMulticaster.addApplicationListener(listener)
    }
  }

  private fun isSynchronous(listener: ApplicationListener<*>): Boolean {
    if (listener.javaClass.getAnnotation(Sync::class.java) != null) {
      return true
    }
    if (listener is InspectableApplicationListenerMethodAdapter &&
      listener.getMethod().getAnnotation(Sync::class.java) != null
    ) {
      return true
    }
    return false
  }

  override fun addApplicationListenerBean(listenerBeanName: String) {
    // Bean-name based listeners are async-only.
    asyncApplicationEventMulticaster.addApplicationListenerBean(listenerBeanName)
  }

  override fun removeApplicationListener(listener: ApplicationListener<*>) {
    asyncApplicationEventMulticaster.removeApplicationListener(listener)
    syncApplicationEventMulticaster.removeApplicationListener(listener)
  }

  override fun removeAllListeners() {
    asyncApplicationEventMulticaster.removeAllListeners()
    syncApplicationEventMulticaster.removeAllListeners()
  }

  override fun removeApplicationListenerBean(listenerBeanName: String) {
    // Bean-name based listeners are async-only.
    asyncApplicationEventMulticaster.removeApplicationListenerBean(listenerBeanName)
  }

  override fun removeApplicationListeners(predicate: Predicate<ApplicationListener<*>>) {
    asyncApplicationEventMulticaster.removeApplicationListeners(predicate)
    syncApplicationEventMulticaster.removeApplicationListeners(predicate)
  }

  override fun removeApplicationListenerBeans(predicate: Predicate<String>) {
    asyncApplicationEventMulticaster.removeApplicationListenerBeans(predicate)
    syncApplicationEventMulticaster.removeApplicationListenerBeans(predicate)
  }

  override fun setBeanFactory(beanFactory: BeanFactory) {
    if (asyncApplicationEventMulticaster is BeanFactoryAware) {
      asyncApplicationEventMulticaster.setBeanFactory(beanFactory)
    }
    if (syncApplicationEventMulticaster is BeanFactoryAware) {
      syncApplicationEventMulticaster.setBeanFactory(beanFactory)
    }
  }

  override fun setBeanClassLoader(classLoader: ClassLoader) {
    if (asyncApplicationEventMulticaster is BeanClassLoaderAware) {
      asyncApplicationEventMulticaster.setBeanClassLoader(classLoader)
    }
    if (syncApplicationEventMulticaster is BeanClassLoaderAware) {
      syncApplicationEventMulticaster.setBeanClassLoader(classLoader)
    }
  }
}
