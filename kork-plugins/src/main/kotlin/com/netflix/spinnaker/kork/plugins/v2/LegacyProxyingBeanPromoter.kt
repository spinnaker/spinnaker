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

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint
import com.netflix.spinnaker.kork.plugins.proxy.ExtensionInvocationProxy
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationAspect
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationState

/**
 * [BeanPromoter] that wires up the extension invocation proxy before delegating the actual
 * promotion to [beanPromoter].
 *
 * TODO(rz): The existing [ExtensionInvocationProxy] work should be sunset in favor of Spring AOP
 *  now that we have the full power of Spring's IoC available.
 */
class LegacyProxyingBeanPromoter(
  private val beanPromoter: BeanPromoter,
  private val invocationAspects: List<InvocationAspect<*>>,
  private val pluginDescriptor: SpinnakerPluginDescriptor
) : BeanPromoter {

  override fun promote(beanName: String, bean: Any, beanClass: Class<*>) {
    ExtensionInvocationProxy.proxy(
      bean as SpinnakerExtensionPoint,
      invocationAspects as List<InvocationAspect<InvocationState>>,
      pluginDescriptor
    ).let {
      beanPromoter.promote(beanName, it, beanClass)
    }
  }
}
