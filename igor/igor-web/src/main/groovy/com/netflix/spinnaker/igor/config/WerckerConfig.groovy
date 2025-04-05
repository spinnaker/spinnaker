/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.config.WerckerProperties.WerckerHost
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.igor.util.RetrofitUtils
import com.netflix.spinnaker.igor.wercker.WerckerCache
import com.netflix.spinnaker.igor.wercker.WerckerClient
import com.netflix.spinnaker.igor.wercker.WerckerService
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import okhttp3.OkHttpClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.converter.jackson.JacksonConverterFactory

import java.util.concurrent.TimeUnit

import javax.validation.Valid

import retrofit2.Retrofit

@Configuration
@Slf4j
@CompileStatic
@ConditionalOnProperty("wercker.enabled")
@EnableConfigurationProperties(WerckerProperties)
class WerckerConfig {
    @Bean
    Map<String, WerckerService> werckerMasters(
      BuildServices buildServices,
      WerckerCache cache,
      IgorConfigurationProperties igorConfigurationProperties,
      @Valid WerckerProperties werckerProperties,
      ObjectMapper objectMapper,
      OkHttp3ClientConfiguration okHttpClientConfig
    ) {
        log.debug "creating werckerMasters"
        Map<String, WerckerService> werckerMasters = werckerProperties?.masters?.collectEntries { WerckerHost host ->
            log.debug "bootstrapping Wercker ${host.address} as ${host.name}"
            [(host.name): new WerckerService(host, cache, werckerClient(host, igorConfigurationProperties.getClient().timeout, objectMapper, okHttpClientConfig), host.permissions.build())]
        }

        buildServices.addServices(werckerMasters)
        werckerMasters
    }

    static WerckerClient werckerClient(
      WerckerHost host,
      int timeout = 30000,
      ObjectMapper objectMapper,
      OkHttp3ClientConfiguration okHttpClientConfig
    ) {

        return new Retrofit.Builder()
                .baseUrl(RetrofitUtils.getBaseUrl(host.address))
                .client(okHttpClientConfig.createForRetrofit2().readTimeout(timeout, TimeUnit.MILLISECONDS).build())
                .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .build()
                .create(WerckerClient)
    }
}
