/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.config

import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext
import com.netflix.spinnaker.gate.retrofit.*
import com.netflix.spinnaker.gate.services.EurekaLookupService
import com.netflix.spinnaker.gate.services.internal.*
import groovy.transform.CompileStatic
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.metrics.repository.MetricRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import redis.clients.jedis.JedisShardInfo
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.converter.JacksonConverter


import static retrofit.Endpoints.newFixedEndpoint

@CompileStatic
@Configuration
@EnableRedisHttpSession
class GateConfig {

  public static final String AUTHENTICATION_REDIRECT_HEADER_NAME = "X-AUTH-REDIRECT-URL"

  @Bean
  JedisConnectionFactory jedisConnectionFactory(@Value('${redis.connection:redis://localhost:6379}') String connection) {
    URI redis = URI.create(connection)
    def factory = new JedisConnectionFactory()
    factory.hostName = redis.host
    factory.port = redis.port
    if (redis.userInfo) {
      factory.password = redis.userInfo.split(":", 2)[1]
    }
    factory
  }

  @Bean
  @ConditionalOnMissingBean(RestTemplate)
  RestTemplate restTemplate() {
    new RestTemplate()
  }

  @Bean
  ExecutorService executorService() {
    Executors.newCachedThreadPool()
  }

  @Bean
  OortService oortDeployService(ServiceConfiguration serviceConfiguration, MetricRepository metricRepository,
                                EurekaLookupService eurekaLookupService) {
    createClient "oort", OortService, serviceConfiguration, eurekaLookupService, metricRepository
  }

  @Bean
  OrcaService orcaService(ServiceConfiguration serviceConfiguration, MetricRepository metricRepository,
                          EurekaLookupService eurekaLookupService) {
    createClient "orca", OrcaService, serviceConfiguration, eurekaLookupService, metricRepository
  }

  @Bean
  Front50Service front50Service(ServiceConfiguration serviceConfiguration, MetricRepository metricRepository,
                                EurekaLookupService eurekaLookupService) {
    createClient "front50", Front50Service, serviceConfiguration, eurekaLookupService, metricRepository
  }

  @Bean
  MortService mortService(ServiceConfiguration serviceConfiguration, MetricRepository metricRepository,
                          EurekaLookupService eurekaLookupService) {
    createClient "mort", MortService, serviceConfiguration, eurekaLookupService, metricRepository
  }

  @Bean
  KatoService katoService(ServiceConfiguration serviceConfiguration, MetricRepository metricRepository,
                          EurekaLookupService eurekaLookupService) {
    createClient "kato", KatoService, serviceConfiguration, eurekaLookupService, metricRepository
  }

  //---- optional backend components:
  @Bean
  @ConditionalOnProperty('services.echo.enabled')
  EchoService echoService(ServiceConfiguration serviceConfiguration, MetricRepository metricRepository,
                          EurekaLookupService eurekaLookupService) {
    createClient "echo", EchoService, serviceConfiguration, eurekaLookupService, metricRepository
  }

  @Bean
  @ConditionalOnProperty('services.flapjack.enabled')
  FlapJackService flapJackService(ServiceConfiguration serviceConfiguration, MetricRepository metricRepository,
                                  EurekaLookupService eurekaLookupService) {
    createClient "flapjack", FlapJackService, serviceConfiguration, eurekaLookupService, metricRepository
  }

  @Bean
  @ConditionalOnProperty('services.mayo.enabled')
  MayoService mayoService(ServiceConfiguration serviceConfiguration, MetricRepository metricRepository,
                          EurekaLookupService eurekaLookupService) {
    createClient "mayo", MayoService, serviceConfiguration, eurekaLookupService, metricRepository
  }

  @Bean
  @ConditionalOnProperty('services.igor.enabled')
  IgorService igorService(ServiceConfiguration serviceConfiguration, MetricRepository metricRepository,
                          EurekaLookupService eurekaLookupService) {
    createClient "igor", IgorService, serviceConfiguration, eurekaLookupService, metricRepository
  }

  private
  static <T> T createClient(String serviceName, Class<T> type, ServiceConfiguration serviceConfiguration, EurekaLookupService eurekaLookupService, MetricRepository metricRepository) {
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

    def client = endpoint.url ==~ EurekaOkClient.NIWS_SCHEME_PATTERN ?
        new EurekaOkClient(metricRepository, serviceName, eurekaLookupService) :
        new MetricsInstrumentedOkClient(metricRepository, serviceName)

    new RestAdapter.Builder()
        .setEndpoint(endpoint)
        .setClient(client)
        .setConverter(new JacksonConverter())
        .setLogLevel(RestAdapter.LogLevel.BASIC)
        .setLog(new Slf4jRetrofitLogger(type))
        .build()
        .create(type)
  }

  @Bean
  Filter simpleCORSFilter() {
    new Filter() {
      public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
          throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        HttpServletRequest request = (HttpServletRequest) req;
        String origin = request.getHeader("Origin") ?: "*"
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "x-requested-with, content-type");
        response.setHeader("Access-Control-Expose-Headers", [AUTHENTICATION_REDIRECT_HEADER_NAME].join(", "))
        chain.doFilter(req, res);
      }

      public void init(FilterConfig filterConfig) {}

      public void destroy() {}
    }
  }

  @Component
  static class HystrixFilter implements Filter {
    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
      HystrixRequestContext.initializeContext()
      chain.doFilter(request, response)
    }

    void init(FilterConfig filterConfig) throws ServletException {}

    void destroy() {}
  }
}
