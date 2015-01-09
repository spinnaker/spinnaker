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

package com.netflix.spinnaker.orca.rush.config

import groovy.transform.CompileStatic
import java.lang.reflect.Type
import com.google.gson.*
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.rush.api.RushService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client
import retrofit.converter.GsonConverter
import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import([OrcaConfiguration, RetrofitConfiguration])
@ComponentScan([
    "com.netflix.spinnaker.orca.rush.pipeline",
    "com.netflix.spinnaker.orca.rush.tasks"
])
@ConditionalOnProperty("rush.baseUrl")
@CompileStatic
class RushConfiguration {

  @Autowired
  Client retrofitClient
  @Autowired
  LogLevel retrofitLogLevel

  @Bean
  Endpoint rushEndpoint(
      @Value('${rush.baseUrl:http://rush.prod.netflix.net}') String rushBaseUrl) {
    newFixedEndpoint(rushBaseUrl)
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

}
