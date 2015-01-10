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

package com.netflix.spinnaker.orca.echo.config

import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.echo.spring.EchoNotifyingPipelineExecutionListener
import com.netflix.spinnaker.orca.echo.spring.EchoNotifyingStageExecutionListener
import com.netflix.spinnaker.orca.notifications.*
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import net.greghaines.jesque.client.Client as JesqueClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.converter.GsonConverter
import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import(RetrofitConfiguration)
@ConditionalOnProperty(value = 'echo.baseUrl')
@CompileStatic
class EchoConfiguration {

  @Autowired Client retrofitClient
  @Autowired RestAdapter.LogLevel retrofitLogLevel

  @Bean Endpoint echoEndpoint(
      @Value('${echo.baseUrl:http://echo.prod.netflix.net}') String echoBaseUrl) {
    newFixedEndpoint(echoBaseUrl)
  }

  @Bean EchoService echoService(Endpoint echoEndpoint, Gson gson) {
    new RestAdapter.Builder()
        .setEndpoint(echoEndpoint)
        .setClient(retrofitClient)
        .setLogLevel(retrofitLogLevel)
        .setConverter(new GsonConverter(gson))
        .build()
        .create(EchoService)
  }

  @Bean BuildJobNotificationHandler buildJobNotificationHandler() {
    new BuildJobNotificationHandler()
  }

  @Bean
  BuildJobPollingNotificationAgent buildJobPollingNotificationAgent(
      ObjectMapper objectMapper,
      EchoService echoService,
      JesqueClient jesqueClient,
      List<NotificationHandler> notificationHandlers) {
    new BuildJobPollingNotificationAgent(objectMapper, echoService, jesqueClient, notificationHandlers)
  }

  @Bean ManualTriggerNotificationHandler manualTriggerNotificationHandler() {
    new ManualTriggerNotificationHandler()
  }

  @Bean
  ManualTriggerPollingNotificationAgent manualTriggerPollingNotificationAgent(
      ObjectMapper objectMapper,
      EchoService echoService,
      JesqueClient jesqueClient,
      List<NotificationHandler> notificationHandlers) {
    new ManualTriggerPollingNotificationAgent(objectMapper, echoService, jesqueClient, notificationHandlers)
  }

  @Bean EchoNotifyingStageExecutionListener echoNotifyingStageExecutionListener(
      ExecutionRepository executionRepository,
      EchoService echoService) {
    new EchoNotifyingStageExecutionListener(executionRepository, echoService)
  }

  @Bean
  EchoNotifyingPipelineExecutionListener echoNotifyingPipelineExecutionListener(
      ExecutionRepository executionRepository,
      EchoService echoService) {
    new EchoNotifyingPipelineExecutionListener(executionRepository, echoService)
  }
}
