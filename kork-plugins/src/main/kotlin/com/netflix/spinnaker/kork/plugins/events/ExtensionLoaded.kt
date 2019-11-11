/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.plugins.events

import com.netflix.spinnaker.kork.plugins.ExtensionBeanDefinitionRegistryPostProcessor
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import org.springframework.context.ApplicationEvent

/**
 * A Spring [ApplicationEvent] that is emitted when an extension is loaded.
 *
 * @param source The source of the event
 * @param beanName The name of the extension Spring bean for the extension
 * @param beanClass The extension bean type
 * @param pluginDescriptor The plugin descriptor, if the extension was provided by a plugin
 */
class ExtensionLoaded(
  source: ExtensionBeanDefinitionRegistryPostProcessor,
  val beanName: String,
  val beanClass: Class<*>,
  val pluginDescriptor: SpinnakerPluginDescriptor? = null
) : ApplicationEvent(source)
