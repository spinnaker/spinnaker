package com.netflix.spinnaker.orca.mine.config

import com.google.gson.Gson
import com.netflix.spinnaker.orca.mine.Canary
import com.netflix.spinnaker.orca.mine.CanaryAnalysisResult
import com.netflix.spinnaker.orca.mine.CanaryResult
import com.netflix.spinnaker.orca.mine.Health
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.Status
import com.netflix.spinnaker.orca.mine.TimeDuration
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
import retrofit.http.Path

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
      final Map<String, Canary> canaries = new ConcurrentHashMap<>()

      @Override
      Canary terminateCanary(@Path('id') String id) {
        Canary c = canaries.get(id)
        if (!c) {
          return null
        }

        c.status = new Status(complete: true, status: 'TERMINATED')
        return c
      }

      @Override
      String registerCanary(Canary canary) {
        canary.id = UUID.randomUUID().toString()
        canary.launchedDate = System.currentTimeMillis()
        canary.status = new Status(complete: false, status: 'LAUNCHED')

        canaries.put(canary.id, canary)

        return canary.id
      }

      @Override
      Canary checkCanaryStatus(String id) {
        Canary c = canaries.get(id)
        if (!c) {
          return null
        }

        if (c.status.complete) {
          return c
        }

        if (!c.health) {
          c.health = new Health('HEALTHY', 'healthy')
        }

        if (!c.canaryResult) {
          c.canaryResult = new CanaryResult()
          c.canaryResult.canarySuccessCriteria = c.canaryConfig.canarySuccessCriteria
          c.canaryResult.combinedScoreStrategy = c.canaryConfig.combinedCanaryResultStrategy
          c.canaryResult.overallResult = "SUCCESS"
          c.canaryResult.overallScore = c.canaryConfig.canarySuccessCriteria.canaryResultScore - 1
          c.canaryResult.lastCanaryAnalysisResults = c.canaryDeployments.collect {
            new CanaryAnalysisResult(id: UUID.randomUUID().toString(),
              canaryDeploymentId: it.id,
              score: c.canaryResult.overallScore,
              result: 'SUCCESS',
              timeDuration: new TimeDuration(TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - c.launchedDate).intValue(), 'M'),
              lastUpdated: System.currentTimeMillis(),
              canaryReportURL: "http://spinnaker.prod.netflix.net/",
              additionalAttributes: [:]
            )
          }
        }

        double rand = Math.random()
        if (rand < 0.4) {
          return c
        } else if (rand < 0.5) {
          c.health.health = Health.UNHEALTHY
          c.health.message = 'unhealthy'
          return c
        }

        c.status.complete = true

        rand = Math.random()

        if (rand < 0.3) {
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
