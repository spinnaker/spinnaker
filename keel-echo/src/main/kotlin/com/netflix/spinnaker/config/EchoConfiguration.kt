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
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.jonpeterson.jackson.module.versioning.VersioningModule
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.attribute.Attribute
import com.netflix.spinnaker.keel.echo.EchoService
import com.netflix.spinnaker.keel.echo.EventNotificationListener
import com.netflix.spinnaker.keel.findAllSubtypes
import com.netflix.spinnaker.keel.policy.*
import com.netflix.spinnaker.keel.retrofit.RetrofitConfiguration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.Endpoints
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.converter.JacksonConverter

@Configuration
@ComponentScan(basePackages = arrayOf(
  "com.netflix.spinnaker.keel.echo"
))
@Import(RetrofitConfiguration::class)
open class EchoConfiguration {

  private val log = LoggerFactory.getLogger(javaClass)

  @Bean open fun echoEndpoint(@Value("\${echo.baseUrl}") echoBaseUrl: String)
    = Endpoints.newFixedEndpoint(echoBaseUrl)

  @Bean open fun echoService(echoEndpoint: Endpoint,
                             objectMapper: ObjectMapper,
                             retrofitClient: Client,
                             spinnakerRequestInterceptor: RequestInterceptor,
                             retrofitLogLevel: RestAdapter.LogLevel)
    = RestAdapter.Builder()
    .setRequestInterceptor(spinnakerRequestInterceptor)
    .setEndpoint(echoEndpoint)
    .setClient(retrofitClient)
    .setLogLevel(retrofitLogLevel)
    .setConverter(JacksonConverter(objectMapper))
    .build()
    .create(EchoService::class.java)

  @Autowired
  open fun objectMapper(objectMapper: ObjectMapper) =
    objectMapper.apply {
      registerSubtypes(*findAllSubtypes(log, NotificationSpec::class.java, "com.netflix.spinnaker.keel.intent"))
    }
}
