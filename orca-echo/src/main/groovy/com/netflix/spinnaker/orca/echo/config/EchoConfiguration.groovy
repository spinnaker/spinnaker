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
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.jenkins.BuildJobNotificationHandler
import com.netflix.spinnaker.orca.notifications.manual.ManualTriggerNotificationHandler
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import net.greghaines.jesque.Config
import net.greghaines.jesque.ConfigBuilder
import net.greghaines.jesque.client.Client as JesqueClient
import net.greghaines.jesque.client.ClientImpl
import net.lariverosc.jesquespring.SpringWorkerFactory
import net.lariverosc.jesquespring.SpringWorkerPool
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
@Import(RetrofitConfiguration)
@ConditionalOnProperty(value = 'echo.baseUrl')
@ComponentScan([
    "com.netflix.spinnaker.orca.echo",
    "com.netflix.spinnaker.orca.notifications.jenkins",
    "com.netflix.spinnaker.orca.notifications.manual"
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

  @Bean
  @ConditionalOnProperty("redis.connection")
  Config jesqueConfig(@Value('${redis.connection:redis://localhost:6379}')
                          String connection) {
    def jedisConnection = URI.create(connection)
    new ConfigBuilder()
        .withHost(jedisConnection.host)
        .withPort(jedisConnection.port)
        .build()
  }

  @Bean
  JesqueClient jesqueClient(Config jesqueConfig) {
    new ClientImpl(jesqueConfig)
  }

  @Bean @Scope(SCOPE_PROTOTYPE)
  BuildJobNotificationHandler buildJobNotificationHandler(Map input) {
    new BuildJobNotificationHandler(input)
  }

  @Bean @Scope(SCOPE_PROTOTYPE)
  ManualTriggerNotificationHandler manualTriggerNotificationHandler(Map input) {
    new ManualTriggerNotificationHandler(input)
  }

  @Bean
  SpringWorkerFactory workerFactory(Config jesqueConfig, List<AbstractPollingNotificationAgent> notificationAgents) {
    new SpringWorkerFactory(jesqueConfig, notificationAgents.collect {
      it.notificationType
    })
  }

  @Bean(initMethod = "init", destroyMethod = "destroy")
  SpringWorkerPool workerPool(SpringWorkerFactory workerFactory,
                              @Value('${jesque.numWorkers:1}') int numWorkers) {
    new SpringWorkerPool(workerFactory, numWorkers)
  }
}
