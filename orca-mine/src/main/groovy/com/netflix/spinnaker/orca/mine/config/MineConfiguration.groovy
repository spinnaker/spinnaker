package com.netflix.spinnaker.orca.mine.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.netflix.spinnaker.orca.mine.Canary
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.Status
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.client.Client

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import([RetrofitConfiguration])
@ComponentScan([
  "com.netflix.spinnaker.orca.mine.pipeline",
  "com.netflix.spinnaker.orca.mine.tasks"
])
@ConditionalOnProperty(value = 'mine.baseUrl')
class MineConfiguration {

  @Autowired
  Client retrofitClient
  @Autowired
  RestAdapter.LogLevel retrofitLogLevel

  @Bean
  Endpoint mineEndpoint(
    @Value('${mine.baseUrl:http://mine-main.prod.netflix.net}') String mineBaseUrl) {
    newFixedEndpoint(mineBaseUrl)
  }

  @Bean
  MineService mineService(Endpoint mineEndpoint, Gson gson) {
    return new MineService() {
      final Map<String, Map> canaries = new ConcurrentHashMap<>()

      @Override
      Map registerCanary(String app, Canary canary) {
        canary.id = UUID.randomUUID().toString()
        canary.launchedDate = System.currentTimeMillis()
        canary.status = new Status(complete: false, status: 'LAUNCHED')

        Map ctx = new ObjectMapper().convertValue(canary, Map)

        canaries.put(canary.id, ctx)

        return ctx
      }

      @Override
      Map checkCanaryStatus(String id) {
        Map c = canaries.get(id)
        if (!c) {
          return null
        }

        if (c.status.complete) {
          return c
        }

        if (System.currentTimeMillis() - c.launchedDate < TimeUnit.HOURS.toMillis(c.canaryConfig.lifetimeHours as Long)) {
          return c
        }

        double rand = Math.random()
        if (rand < 0.2) {
          return c
        }


        c.status.complete = true

        if (rand < 0.4) {
          c.status.status = 'FAILED'
        } else {
          c.status.status = 'COMPLETED'
        }

        return c
      }
    }
    /*
    new RestAdapter.Builder()
      .setEndpoint(mineEndpoint)
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(MineService))
      .setConverter(new GsonConverter(gson))
      .build()
      .create(MineService)
      */
  }
}
