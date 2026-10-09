/*
 * Copyright 2026 Netflix, Inc.
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
package com.netflix.spinnaker.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tools.jackson.databind.json.JsonMapper

/**
 * Spring Boot 4 no longer wires the application's [JsonMapper] into the MVC JSON message
 * converter automatically: with Jackson 2 still on the classpath the vanilla
 * `MappingJackson2HttpMessageConverter` wins and responses lose Keel's serialization
 * customization (mixins, custom serializers, NON_NULL inclusion). Register our configured
 * mapper explicitly, ahead of the defaults.
 *
 * Kept separate from [DefaultConfiguration] so injecting the mapper cannot introduce a
 * circular reference with the configuration that defines it.
 */
@Configuration
class KeelJacksonMessageConverterConfiguration(
  private val jsonMapper: JsonMapper
) : WebMvcConfigurer {
  override fun extendMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
    converters.add(0, JacksonJsonHttpMessageConverter(jsonMapper))
  }
}
