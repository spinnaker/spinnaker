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
import com.google.gson.Gson
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.echo.spring.EchoNotifyingPipelineExecutionListener
import com.netflix.spinnaker.orca.echo.spring.EchoNotifyingStageExecutionListener
import com.netflix.spinnaker.orca.notifications.jenkins.BuildJobNotificationHandler
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.*
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.client.Client as RetrofitClient
import retrofit.converter.GsonConverter
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE
import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import([RetrofitConfiguration, JesqueConfiguration])
@ConditionalOnProperty(value = 'echo.baseUrl')
@ComponentScan([
    "com.netflix.spinnaker.orca.echo",
    "com.netflix.spinnaker.orca.notifications.jenkins"
])
@CompileStatic
class EchoConfiguration {

  @Autowired RetrofitClient retrofitClient
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

  @Bean @Scope(SCOPE_PROTOTYPE)
  BuildJobNotificationHandler buildJobNotificationHandler(Map input) {
    new BuildJobNotificationHandler(input)
  }
}
