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


package com.netflix.spinnaker.orca.retrofit

import com.netflix.spinnaker.config.OkHttpClientConfiguration
import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler
import com.squareup.okhttp.Interceptor
import com.squareup.okhttp.Response
import groovy.transform.CompileStatic
import com.google.common.base.Optional
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.netflix.spinnaker.orca.retrofit.gson.GsonOptionalDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client
import retrofit.client.OkClient

@Configuration
@CompileStatic
@EnableConfigurationProperties
class RetrofitConfiguration {

   @Bean
   @ConfigurationProperties('okHttpClient')
   OkHttpClientConfiguration okHttpClientConfig() {
     new OkHttpClientConfiguration()
   }

   @Bean
   @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
   Client retrofitClient(OkHttpClientConfiguration okHttpClientConfig) {
     def cfg = okHttpClientConfig.create()
     cfg.networkInterceptors().add(new Interceptor() {
       @Override
       Response intercept(Interceptor.Chain chain) throws IOException {
         def userAgent = "Spinnaker-${System.getProperty('spring.config.name', 'unknown')}/1.0"
         def req = chain.request().newBuilder().removeHeader('User-Agent').addHeader('User-Agent', userAgent).build()
         chain.proceed(req)
       }
     })
    new OkClient(okHttpClientConfig.create())
  }

  @Bean LogLevel retrofitLogLevel(@Value('${retrofit.logLevel:BASIC}') String retrofitLogLevel) {
    return LogLevel.valueOf(retrofitLogLevel)
  }

  @Bean Gson gson() {
    new GsonBuilder()
        .registerTypeAdapter(Optional, new GsonOptionalDeserializer())
        .create()
  }

  @Bean @Order(Ordered.HIGHEST_PRECEDENCE) RetrofitExceptionHandler retrofitExceptionHandler() {
    new RetrofitExceptionHandler()
  }

}
