/*
 * Copyright 2015 Netflix, Inc.
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



package com.netflix.spinnaker.echo.config

import retrofit.converter.JacksonConverter

import static retrofit.Endpoints.newFixedEndpoint

import com.netflix.spinnaker.echo.hipchat.HipchatService
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.client.Client

@Configuration
@ConditionalOnProperty('hipchat.enabled')
@Slf4j
@CompileStatic
class HipchatConfig {

    @Bean
    Endpoint hipchatEndpoint(@Value('${hipchat.baseUrl}') String hipchatBaseUrl) {
        newFixedEndpoint(hipchatBaseUrl)
    }

    @Bean
    HipchatService hipchatService(Endpoint hipchatEndpoint, Client retrofitClient, RestAdapter.LogLevel retrofitLogLevel) {

        log.info('hipchat service loaded')

        new RestAdapter.Builder()
                .setEndpoint(hipchatEndpoint)
                .setConverter(new JacksonConverter())
                .setClient(retrofitClient)
                .setLogLevel(retrofitLogLevel)
                .setLog(new Slf4jRetrofitLogger(HipchatService.class))
                .build()
                .create(HipchatService.class)
    }

}
