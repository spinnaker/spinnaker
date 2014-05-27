package com.netflix.spinnaker.orca.kato.config

import com.netflix.spinnaker.orca.kato.api.AmazonService
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import(RetrofitConfiguration)
@CompileStatic
class KatoConfiguration {

    @Autowired Client retrofitClient
    @Autowired LogLevel retrofitLogLevel

    @Bean
    Endpoint katoEndpoint() {
        newFixedEndpoint("http://kato.test.netflix.net:7001")
    }

    @Bean
    AmazonService amazonService(Endpoint katoEndpoint) {
        new RestAdapter.Builder()
            .setEndpoint(katoEndpoint)
            .setClient(retrofitClient)
            .setLogLevel(retrofitLogLevel)
            .build()
            .create(AmazonService)
    }

}
