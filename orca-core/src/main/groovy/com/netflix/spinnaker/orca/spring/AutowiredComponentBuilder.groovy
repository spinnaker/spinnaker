/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.spring

import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

/**
 * Implemented by components that want to construct beans at runtime and autowire them using the application context.
 */
@CompileStatic
trait AutowiredComponentBuilder implements ApplicationContextAware {

  private ApplicationContext applicationContext

  void autowire(bean) {
    applicationContext.autowireCapableBeanFactory.autowireBean(bean)
  }

  @Autowired
  void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext
  }
}
