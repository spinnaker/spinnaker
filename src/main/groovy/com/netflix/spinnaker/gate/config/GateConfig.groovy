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
import javax.servlet.*
import org.springframework.beans.factory.annotation.Value
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


import static retrofit.Endpoints.newFixedEndpoint

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
  PondService pondService(ServiceConfiguration serviceConfiguration,
                          Client retrofitClient) {
    createClient "pond", PondService, serviceConfiguration, retrofitClient
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

  private
  static <T> T createClient(String serviceName, Class<T> type, ServiceConfiguration serviceConfiguration, Client client) {
    def endpoint = serviceConfiguration.discoveryHosts ?
        newFixedEndpoint("niws://${serviceConfiguration.getSerivce(serviceName).name}")
        : newFixedEndpoint(serviceConfiguration.getSerivce(serviceName).baseUrl)

    new RestAdapter.Builder()
        .setEndpoint(endpoint)
        .setClient(client)
        .setLogLevel(RestAdapter.LogLevel.FULL)
        .build()
        .create(type)
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
