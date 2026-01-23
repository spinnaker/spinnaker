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
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils
import com.netflix.spinnaker.orca.events.ExecutionEvent
import com.netflix.spinnaker.orca.events.ExecutionListenerAdapter
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.spring.DependentPipelineExecutionListener
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import groovy.transform.CompileStatic
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.concurrent.TimeUnit

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

  @Bean
  Front50Service front50Service(ObjectMapper mapper, Front50ConfigurationProperties front50ConfigurationProperties) {
    String baseUrl = RetrofitUtils.getBaseUrl(front50ConfigurationProperties.baseUrl)
    OkHttpClient okHttpClient = clientProvider.getClient(new DefaultServiceEndpoint("front50", baseUrl));
    okHttpClient = okHttpClient.newBuilder()
        .readTimeout(front50ConfigurationProperties.okhttp.readTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(front50ConfigurationProperties.okhttp.writeTimeoutMs, TimeUnit.MILLISECONDS)
        .connectTimeout(front50ConfigurationProperties.okhttp.connectTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create(mapper))
        .build()
        .create(Front50Service)
  }

  @Bean
  ApplicationListener<ExecutionEvent> dependentPipelineExecutionListenerAdapter(DependentPipelineExecutionListener delegate, ExecutionRepository repository) {
    return new ExecutionListenerAdapter(delegate, repository)
  }
}
