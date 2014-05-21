package com.netflix.spinnaker.orca.bakery.config

import com.google.gson.GsonBuilder
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import groovy.transform.CompileStatic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel
import retrofit.converter.GsonConverter

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES
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
        def gson = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .setDateFormat("YYYYMMDDHHmm")
            .create()

        new RestAdapter.Builder()
            .setEndpoint(bakeryEndpoint)
            .setConverter(new GsonConverter(gson))
            .setLogLevel(bakeryLogLevel)
            .build()
            .create(BakeryService)
    }

}
