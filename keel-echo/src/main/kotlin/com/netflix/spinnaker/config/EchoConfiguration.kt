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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.echo.EchoService
import com.netflix.spinnaker.keel.echo.NotificationAttribute
import com.netflix.spinnaker.keel.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer.ClassSubtypeLocator
import org.springframework.beans.factory.annotation.Value
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
@ComponentScan(basePackages = ["com.netflix.spinnaker.keel.echo"])
@Import(RetrofitConfiguration::class)
open class EchoConfiguration {

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

  @Bean open fun notificationSpecSubtype() =
    ClassSubtypeLocator(NotificationAttribute::class.java, listOf("com.netflix.spinnaker.keel.echo"))
}
