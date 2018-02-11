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
package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.jonpeterson.jackson.module.versioning.VersioningModule
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer.SubtypeLocator

/**
 * Applies the default configuration for Keel to an ObjectMapper.
 */
fun configureObjectMapper(mapper: ObjectMapper,
                          properties: KeelProperties,
                          locators: List<SubtypeLocator>): ObjectMapper {
  return mapper
    .apply {
      ObjectMapperSubtypeConfigurer(true).registerSubtypes(this, locators)
      if (properties.prettyPrintJson) {
        enable(SerializationFeature.INDENT_OUTPUT)
      }
    }
    .registerModule(KotlinModule())
    .registerModule(VersioningModule())
    .registerModule(JavaTimeModule())
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
}
