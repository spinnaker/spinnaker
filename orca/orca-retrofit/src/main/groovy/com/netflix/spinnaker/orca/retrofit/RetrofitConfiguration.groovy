/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.orca.retrofit

import com.netflix.spinnaker.orca.retrofit.exceptions.SpinnakerServerExceptionHandler
import groovy.transform.CompileStatic
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@Configuration
@CompileStatic
@EnableConfigurationProperties
class RetrofitConfiguration {
  /**
   * Set the order such that this has higher precedence than the
   * DefaultExceptionHandler bean.
   */
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  SpinnakerServerExceptionHandler spinnakerServerExceptionHandler() {
    new SpinnakerServerExceptionHandler()
  }
}
