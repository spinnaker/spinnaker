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

import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger

import static retrofit.Endpoints.newFixedEndpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.twilio.TwilioService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.codec.binary.Base64
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoint
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.converter.JacksonConverter

@Configuration
@ConditionalOnProperty('twilio.enabled')
@Slf4j
@CompileStatic
class TwilioConfig {

    @Bean
    Endpoint twilioEndpoint(@Value('${twilio.base-url:https://api.twilio.com/}') String twilioBaseUrl) {
        newFixedEndpoint(twilioBaseUrl)
    }

    @Bean
    TwilioService twilioService(
            @Value('${twilio.account}') String username,
            @Value('${twilio.token}') String password,
            Endpoint twilioEndpoint,
            Client retrofitClient,
            RestAdapter.LogLevel retrofitLogLevel) {

        log.info('twilio service loaded')

        RequestInterceptor authInterceptor = new RequestInterceptor() {
            @Override
            public void intercept(RequestInterceptor.RequestFacade request) {
                String auth = "Basic " + Base64.encodeBase64String("${username}:${password}".getBytes())
                request.addHeader("Authorization", auth)
            }
        }

        JacksonConverter converter = new JacksonConverter(EchoObjectMapper.getInstance())

        new RestAdapter.Builder()
                .setEndpoint(twilioEndpoint)
                .setRequestInterceptor(authInterceptor)
                .setClient(retrofitClient)
                .setLogLevel(retrofitLogLevel)
                .setLog(new Slf4jRetrofitLogger(TwilioService.class))
                .setConverter(converter)
                .build()
                .create(TwilioService.class)
    }

}
