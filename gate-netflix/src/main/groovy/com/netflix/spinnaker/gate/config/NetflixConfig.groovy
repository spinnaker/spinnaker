package com.netflix.spinnaker.gate.config

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.gate.retrofit.EurekaOkClient
import com.netflix.spinnaker.gate.retrofit.Slf4jRetrofitLogger
import com.netflix.spinnaker.gate.services.EurekaLookupService
import com.netflix.spinnaker.internal.services.internal.FlexService
import com.netflix.spinnaker.internal.services.internal.MaheService
import com.netflix.spinnaker.internal.services.internal.MineService
import com.squareup.okhttp.OkHttpClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.metrics.repository.MetricRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import retrofit.Endpoint
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.converter.JacksonConverter
import retrofit.http.Body
import retrofit.http.Path
import retrofit.http.Query

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@ConditionalOnExpression('${netflix.enabled:false}')
@ComponentScan(["com.netflix.spinnaker.internal"])
class NetflixConfig {
  @Value('${retrofit.logLevel:BASIC}')
  String retrofitLogLevel

  @Autowired
  RequestInterceptor spinnakerRequestInterceptor

  @Autowired
  ServiceConfiguration serviceConfiguration

  @Autowired
  ExtendedRegistry extendedRegistry

  @Autowired
  EurekaLookupService eurekaLookupService

  @Bean
  @ConditionalOnProperty('services.mahe.enabled')
  MaheService maheService(OkHttpClient okHttpClient) {
    createClient "mahe", MaheService, okHttpClient
  }

  @Bean
  @ConditionalOnMissingBean(MaheService)
  MaheService noopMaheService() {
    new MaheService() {
      @Override
      Map getFastPropertiesByApplication(@Path("appName") String appName) {
        return [:]
      }

      @Override
      Map getAll() {
        return [:]
      }

      @Override
      Map getByKey(@Path("key") String key) {
        return [:]
      }

      @Override
      List<String> getAllKeys() {
        return []
      }

      @Override
      Map getImpact(@Body Map scope) {
        return [:]
      }

      @Override
      Map queryScope(@Body Map scope) {
        return [:]
      }

      @Override
      Map create(@Body Map fastProperty) {
        return [:]
      }

      @Override
      String promote(@Body Map fastProperty) {
        return [:]
      }

      @Override
      Map promotionStatus(@Path("promotionId") String promotionId) {
        return [:]
      }

      @Override
      Map passPromotion(@Path("promotionId") String promotionId, @Body Boolean pass) {
        return [:]
      }

      @Override
      List promotions() {
        return []
      }

      @Override
      List promotionsByApp(@Path("appId") String appId) {
        return []
      }

      @Override
      Map delete(@Query("propId") String propId, @Query("cmcTicket") String cmcTicket, @Query("env") String env) {
        return [:]
      }
    }
  }

  @Bean
  @ConditionalOnProperty('services.flex.enabled')
  FlexService flexService(OkHttpClient okHttpClient) {
    createClient "flex", FlexService, okHttpClient
  }

  @Bean
  @ConditionalOnMissingBean(FlexService)
  FlexService noopFlexService(ServiceConfiguration serviceConfiguration, MetricRepository metricRepository,
                              EurekaLookupService eurekaLookupService) {
    return new FlexService() {
      @Override
      List<Map> getForCluster(@Path("application") String application,
                              @Path("account") String account,
                              @Path("cluster") String cluster) {
        return []
      }

      @Override
      List<Map> getForClusterAndRegion(@Path("application") String application,
                                       @Path("account") String account,
                                       @Path("cluster") String cluster,
                                       @Path("region") String region) {
        return []
      }

      @Override
      List<Map> getForAccount(@Path("account") String account) {
        return []
      }

      @Override
      List<Map> getForAccountAndRegion(@Path("account") String account, @Query("region") String region) {
        return []
      }
    }
  }

  @Bean
  @ConditionalOnProperty('services.mine.enabled')
  MineService mineService(OkHttpClient okHttpClient) {
    createClient "mine", MineService, okHttpClient
  }

  private <T> T createClient(String serviceName, Class<T> type, OkHttpClient okHttpClient) {
    Service service = serviceConfiguration.getService(serviceName)
    if (service == null) {
      throw new IllegalArgumentException("Unknown service ${serviceName} requested of type ${type}")
    }
    if (!service.enabled) {
      return null
    }
    Endpoint endpoint = serviceConfiguration.discoveryHosts && service.vipAddress ?
      newFixedEndpoint("niws://${service.vipAddress}")
      : newFixedEndpoint(service.baseUrl)

    def client = new EurekaOkClient(okHttpClient, extendedRegistry, serviceName, eurekaLookupService)

    new RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(endpoint)
      .setClient(client)
      .setConverter(new JacksonConverter())
      .setLogLevel(RestAdapter.LogLevel.valueOf(retrofitLogLevel))
      .setLog(new Slf4jRetrofitLogger(type))
      .build()
      .create(type)
  }
}
