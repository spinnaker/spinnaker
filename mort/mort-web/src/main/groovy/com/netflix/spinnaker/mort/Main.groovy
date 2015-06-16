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

package com.netflix.spinnaker.mort

import com.netflix.spinnaker.mort.model.CachingAgentScheduler
import com.netflix.spinnaker.mort.rx.RxCachingAgentScheduler
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.annotation.*
import org.springframework.web.filter.ShallowEtagHeaderFilter
import javax.servlet.Filter

@Configuration
@ComponentScan(['com.netflix.spinnaker.mort.config', 'com.netflix.spinnaker.mort.web', 'com.netflix.spinnaker.mort.filters', 'com.netflix.spinnaker.config'])
class Main {
  @Bean
  @ConditionalOnMissingBean(CachingAgentScheduler)
  CachingAgentScheduler cachingAgentScheduler() {
    new RxCachingAgentScheduler()
  }
}
