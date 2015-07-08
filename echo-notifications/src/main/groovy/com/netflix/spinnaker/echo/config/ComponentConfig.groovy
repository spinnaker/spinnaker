/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.config

import com.netflix.appinfo.InstanceInfo
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType

/**
 * Finds spring beans (@Component, @Resource, @Controller, etc.) on your classpath.
 * If you don't like classpath scanning, don't use it, you'll always have the choice.
 * I generally exclude @Configuration's from this scan, as picking those up can affect your tests.
 */
@Configuration
@ComponentScan(
    basePackages = ['com.netflix.spinnaker.echo'],
    excludeFilters = @ComponentScan.Filter(value = Configuration, type = FilterType.ANNOTATION)
)
class ComponentConfig {
  @Bean
  InstanceInfo.InstanceStatus instanceStatus() {
    InstanceInfo.InstanceStatus.UNKNOWN
  }
}
