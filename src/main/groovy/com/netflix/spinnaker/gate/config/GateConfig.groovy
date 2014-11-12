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
import com.netflix.spinnaker.gate.retrofit.EurekaOkClient
import com.netflix.spinnaker.gate.services.*
import groovy.transform.CompileStatic
import javax.servlet.*
import javax.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter


import static retrofit.Endpoints.newFixedEndpoint

@CompileStatic
@Configuration
class GateConfig {

  @Bean
  @ConditionalOnMissingBean(RestTemplate)
  RestTemplate restTemplate() {
    new RestTemplate()
  }

  @Bean
  Client retrofitClient(ServiceConfiguration serviceConfiguration) {
    serviceConfiguration.discoveryHosts ? new EurekaOkClient() : new OkClient()
  }

  @Bean
  CacheManager cacheManager(Set<Cache> caches) {
    new SimpleCacheManager(caches: caches)
  }

  @Bean
  Cache applicationsCache() {
    new ConcurrentMapCache("applications")
  }

  @Bean
  Cache clustersCache() {
    new ConcurrentMapCache("clusters")
  }

  @Bean
  Cache applicationCache() {
    new ConcurrentMapCache("application")
  }

  @Bean
  OortService oortDeployService(ServiceConfiguration serviceConfiguration,
                                Client retrofitClient) {
    createClient "oort", OortService, serviceConfiguration, retrofitClient
  }

  @Bean
  OrcaService orcaService(ServiceConfiguration serviceConfiguration,
                          Client retrofitClient) {
    createClient "pond", OrcaService, serviceConfiguration, retrofitClient
  }

  @Bean
  EchoService echoService(ServiceConfiguration serviceConfiguration,
                          Client retrofitClient) {
    createClient "echo", EchoService, serviceConfiguration, retrofitClient
  }

  @Bean
  FlapJackService flapJackService(ServiceConfiguration serviceConfiguration,
                                  Client retrofitClient) {
    createClient "flapjack", FlapJackService, serviceConfiguration, retrofitClient
  }

  @Bean
  Front50Service front50Service(ServiceConfiguration serviceConfiguration,
                                Client retrofitClient) {
    createClient "front50", Front50Service, serviceConfiguration, retrofitClient
  }

  private
  static <T> T createClient(String serviceName, Class<T> type, ServiceConfiguration serviceConfiguration, Client client) {
    def endpoint = serviceConfiguration.discoveryHosts && !serviceConfiguration.getSerivce(serviceName)?.baseUrl ?
        newFixedEndpoint("niws://${serviceConfiguration.getSerivce(serviceName).name}")
        : newFixedEndpoint(serviceConfiguration.getSerivce(serviceName).baseUrl)

    new RestAdapter.Builder()
        .setEndpoint(endpoint)
        .setClient(client)
        .setConverter(new JacksonConverter())
        .setLogLevel(RestAdapter.LogLevel.FULL)
        .build()
        .create(type)
  }

  @Bean
  Filter simpleCORSFilter() {
    new Filter() {
      public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "x-requested-with, content-type");
        chain.doFilter(req, res);
      }

      public void init(FilterConfig filterConfig) {}

      public void destroy() {}
    }
  }

  @Component
  static class HystrixFilter implements Filter {
    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
      HystrixRequestContext.initializeContext()
      chain.doFilter(request, response)
    }

    void init(FilterConfig filterConfig) throws ServletException {}

    void destroy() {}
  }
}
