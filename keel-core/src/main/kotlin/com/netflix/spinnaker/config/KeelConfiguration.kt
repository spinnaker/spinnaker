/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.jonpeterson.jackson.module.versioning.VersioningModule
import com.netflix.spinnaker.keel.Intent
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.util.ClassUtils

@Configuration
@ComponentScan(basePackages = arrayOf(
  "com.netflix.spinnaker.keel.intents"
))
open class KeelConfiguration {

  private val log = LoggerFactory.getLogger(javaClass)

  @Bean open fun objectMapper(): ObjectMapper
    = ObjectMapper().apply {
        registerSubtypes(*findAllIntentSubtypes().toTypedArray())
      }
        .registerModule(KotlinModule())
        .registerModule(VersioningModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

  private fun findAllIntentSubtypes(): List<Class<*>> {
    return ClassPathScanningCandidateComponentProvider(false).apply {
      addIncludeFilter(AssignableTypeFilter(Intent::class.java))
    }
      .findCandidateComponents("com.netflix.spinnaker.keel.intents")
      .map {
        val cls = ClassUtils.resolveClassName(it.beanClassName, ClassUtils.getDefaultClassLoader())
        log.info("Registering Intent: ${cls.simpleName}")
        return@map cls
      }
  }
}
