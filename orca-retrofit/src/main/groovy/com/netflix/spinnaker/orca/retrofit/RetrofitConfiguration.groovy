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

import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler
import com.squareup.okhttp.OkHttpClient
import groovy.transform.CompileStatic
import com.google.common.base.Optional
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.netflix.spinnaker.orca.retrofit.gson.GsonOptionalDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client
import retrofit.client.OkClient

import java.util.concurrent.TimeUnit

@Configuration
@CompileStatic
class RetrofitConfiguration {
  @Bean Client retrofitClient(@Value('${retrofit.connectTimeoutMs:15000}') int connectTimeout,
                              @Value('${retrofit.readTimeoutMs:20000}') int readTimeout) {
    def okHttpClient = new OkHttpClient()
    okHttpClient.setConnectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
    okHttpClient.setReadTimeout(readTimeout, TimeUnit.MILLISECONDS)

    return new OkClient(okHttpClient)
  }

  @Bean LogLevel retrofitLogLevel() {
    LogLevel.BASIC
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
