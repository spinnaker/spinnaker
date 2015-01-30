/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.retrofit

//import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler
import groovy.transform.CompileStatic
import com.google.common.base.Optional
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.netflix.spinnaker.rosco.retrofit.gson.GsonOptionalDeserializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client
import retrofit.client.OkClient

@Configuration
@CompileStatic
class RetrofitConfiguration {
  @Bean Client retrofitClient() {
    new OkClient()
  }

  @Bean LogLevel retrofitLogLevel() {
    LogLevel.FULL
  }

  @Bean Gson gson() {
    new GsonBuilder()
        .registerTypeAdapter(Optional, new GsonOptionalDeserializer())
        .create()
  }

//  @Bean RetrofitExceptionHandler retrofitExceptionHandler() {
//    new RetrofitExceptionHandler()
//  }

}
