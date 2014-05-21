package com.netflix.spinnaker.orca.bakery.config

import com.netflix.spinnaker.orca.bakery.api.BakeryService
import groovy.transform.CompileStatic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@CompileStatic
class BakeryConfiguration {

    @Bean
    LogLevel bakeryLogLevel() {
        LogLevel.FULL
    }

    @Bean
    Endpoint bakeryEndpoint() {
        newFixedEndpoint("http://bakery.test.netflix.net:7001")
    }

    @Bean
    BakeryService bakery(Endpoint bakeryEndpoint, LogLevel bakeryLogLevel) {
        new RestAdapter.Builder()
            .setEndpoint(bakeryEndpoint)
            .setLogLevel(bakeryLogLevel)
            .build()
            .create(BakeryService)
    }

}
