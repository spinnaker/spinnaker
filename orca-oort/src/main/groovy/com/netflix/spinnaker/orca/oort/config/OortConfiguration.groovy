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



package com.netflix.spinnaker.orca.oort.config

import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import groovy.transform.CompileStatic
import com.google.gson.Gson
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.converter.GsonConverter
import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import([RetrofitConfiguration])
@ComponentScan([
  "com.netflix.spinnaker.orca.oort.pipeline",
  "com.netflix.spinnaker.orca.oort.tasks"
])
@CompileStatic
class OortConfiguration {

  @Autowired Client retrofitClient
  @Autowired RestAdapter.LogLevel retrofitLogLevel

  @Bean Endpoint oortEndpoint(@Value('${oort.baseUrl:http://oort.prod.netflix.net}') String oortBaseUrl) {
    newFixedEndpoint(oortBaseUrl)
  }

  @Bean OortService oortDeployService(Endpoint oortEndpoint, Gson gson) {
    new RestAdapter.Builder()
        .setEndpoint(oortEndpoint)
        .setClient(retrofitClient)
        .setLogLevel(retrofitLogLevel)
        .setLog(new RetrofitSlf4jLog(OortService))
        .setConverter(new GsonConverter(gson))
        .build()
        .create(OortService)
  }
}
