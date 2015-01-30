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

package com.netflix.spinnaker.rosco.rush.config

import com.google.gson.*
import com.netflix.spinnaker.rosco.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.rosco.rush.api.RushService
import com.netflix.spinnaker.rosco.rush.api.ScriptRequest
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.Endpoints
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client
import retrofit.converter.GsonConverter

import java.lang.reflect.Type

@Configuration
@Import([RetrofitConfiguration])
@CompileStatic
class RushConfiguration {

  @Autowired
  Client retrofitClient
  @Autowired
  LogLevel retrofitLogLevel

  @Bean
  Endpoint rushEndpoint(@Value('${rush.baseUrl:http://rush.prod.netflix.net}') String rushBaseUrl) {
    Endpoints.newFixedEndpoint(rushBaseUrl)
  }

  @Bean
  RushService rushService(Endpoint rushEndpoint) {
    def gson = new GsonBuilder()
      .registerTypeAdapter(Date.class, new JsonDeserializer<Date>() {
        Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
          new Date(json.getAsJsonPrimitive().getAsLong());
        }
      })
      .create()

    new RestAdapter.Builder()
      .setEndpoint(rushEndpoint)
      .setConverter(new GsonConverter(gson))
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .build()
      .create(RushService)
  }

  @Bean
  ScriptRequest scriptRequest(@Value('${rush.credentials}') String credentials, @Value('${rush.image}') String image) {
    new ScriptRequest(credentials: credentials, image: image)
  }

}
