package com.netflix.spinnaker.orca.retrofit

import groovy.transform.CompileStatic
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client
import retrofit.client.OkClient

@Configuration
@EnableBatchProcessing
@CompileStatic
class RetrofitConfiguration {
    @Bean
    Client retrofitClient() {
        new OkClient()
    }

    @Bean
    LogLevel retrofitLogLevel() {
        LogLevel.BASIC
    }
}
