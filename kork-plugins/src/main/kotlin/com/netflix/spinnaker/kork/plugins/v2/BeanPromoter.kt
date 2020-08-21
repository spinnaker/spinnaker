/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.kork.plugins.v2

/**
 * Promotes beans from the plugin application context to the service application context.
 */
interface BeanPromoter {

  /**
   * Promote a single bean.
   *
   * Beans are already filtered by the core framework before they reach this point. Any
   * additional filtering may cause plugins to misbehave.
   *
   * @param beanName The name of the bean within the plugin application context.
   * @param bean The bean object
   * @param beanClass The class of [bean]
   */
  fun promote(beanName: String, bean: Any, beanClass: Class<*>)
}
