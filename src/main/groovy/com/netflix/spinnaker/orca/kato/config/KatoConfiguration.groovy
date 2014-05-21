package com.netflix.spinnaker.orca.kato.config

import com.netflix.spinnaker.orca.kato.api.AmazonService
import groovy.transform.CompileStatic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@CompileStatic
class KatoConfiguration {

    @Bean
    LogLevel katoLogLevel() {
        LogLevel.FULL
    }

    @Bean
    Endpoint katoEndpoint() {
        newFixedEndpoint("http://kato.test.netflix.net:7001")
    }

    @Bean
    AmazonService amazonService(Endpoint katoEndpoint, LogLevel katoLogLevel) {
        new RestAdapter.Builder()
            .setEndpoint(katoEndpoint)
            .setLogLevel(katoLogLevel)
            .build()
            .create(AmazonService)
    }

}
