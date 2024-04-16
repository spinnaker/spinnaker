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

package com.netflix.spinnaker.orca.front50.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.jakewharton.retrofit.Ok3Client
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler
import com.netflix.spinnaker.orca.events.ExecutionEvent
import com.netflix.spinnaker.orca.events.ExecutionListenerAdapter
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.spring.DependentPipelineExecutionListener
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.converter.JacksonConverter

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import(RetrofitConfiguration)
@ComponentScan([
  "com.netflix.spinnaker.orca.front50.pipeline",
  "com.netflix.spinnaker.orca.front50.tasks",
  "com.netflix.spinnaker.orca.front50"
])
@EnableConfigurationProperties(Front50ConfigurationProperties)
@CompileStatic
@ConditionalOnExpression('${front50.enabled:true}')
class Front50Configuration {

  @Autowired
  OkHttpClientProvider clientProvider

  @Autowired
  RestAdapter.LogLevel retrofitLogLevel

  @Autowired
  RequestInterceptor spinnakerRequestInterceptor

  @Bean
  Endpoint front50Endpoint(Front50ConfigurationProperties front50ConfigurationProperties) {
    newFixedEndpoint(front50ConfigurationProperties.getBaseUrl())
  }

  @Bean
  Front50Service front50Service(Endpoint front50Endpoint, ObjectMapper mapper) {
    new RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(front50Endpoint)
      .setClient(new Ok3Client(clientProvider.getClient(new DefaultServiceEndpoint("front50", front50Endpoint.getUrl()))))
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(Front50Service))
      .setConverter(new JacksonConverter(mapper))
      .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
      .build()
      .create(Front50Service)
  }

  @Bean
  ApplicationListener<ExecutionEvent> dependentPipelineExecutionListenerAdapter(DependentPipelineExecutionListener delegate, ExecutionRepository repository) {
    return new ExecutionListenerAdapter(delegate, repository)
  }
}
