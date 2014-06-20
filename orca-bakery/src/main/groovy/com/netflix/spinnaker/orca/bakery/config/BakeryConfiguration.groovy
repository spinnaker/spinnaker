package com.netflix.spinnaker.orca.bakery.config

import com.google.gson.GsonBuilder
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.bakery.job.BakeJobBuilder
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import groovy.transform.CompileStatic
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client
import retrofit.converter.GsonConverter

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES
import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import(RetrofitConfiguration)
@CompileStatic
class BakeryConfiguration {

    @Autowired Client retrofitClient
    @Autowired LogLevel retrofitLogLevel

    @Bean
    Endpoint bakeryEndpoint() {
        newFixedEndpoint("http://bakery.test.netflix.net:7001")
    }

    @Bean
    BakeryService bakery(Endpoint bakeryEndpoint) {
        def gson = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .setDateFormat("YYYYMMDDHHmm")
            .create()

        new RestAdapter.Builder()
            .setEndpoint(bakeryEndpoint)
            .setConverter(new GsonConverter(gson))
            .setClient(retrofitClient)
            .setLogLevel(retrofitLogLevel)
            .build()
            .create(BakeryService)
    }

    @Bean
    BakeJobBuilder bakeJobBuilder(StepBuilderFactory steps, BakeryService bakery) {
        new BakeJobBuilder(steps: steps, bakery: bakery)
    }

}
