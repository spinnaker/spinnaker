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
import com.netflix.spinnaker.gate.retrofit.Slf4jRetrofitLogger
import com.netflix.spinnaker.gate.services.internal.*
import groovy.transform.CompileStatic
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import static retrofit.Endpoints.newFixedEndpoint

@CompileStatic
@Configuration
class GateConfig {

  public static final String AUTHENTICATION_REDIRECT_HEADER_NAME = "X-AUTH-REDIRECT-URL"

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
  Client retrofitClient(ServiceConfiguration serviceConfiguration) {
    serviceConfiguration.discoveryHosts ? new EurekaOkClient() : new OkClient()
  }

  @Bean
  OortService oortDeployService(ServiceConfiguration serviceConfiguration,
                                Client retrofitClient) {
    createClient "oort", OortService, serviceConfiguration, retrofitClient
  }

  @Bean
  OrcaService orcaService(ServiceConfiguration serviceConfiguration,
                          Client retrofitClient) {
    createClient "orca", OrcaService, serviceConfiguration, retrofitClient
  }

  @Bean
  Front50Service front50Service(ServiceConfiguration serviceConfiguration,
                                Client retrofitClient) {
    createClient "front50", Front50Service, serviceConfiguration, retrofitClient
  }

  @Bean
  MortService mortService(ServiceConfiguration serviceConfiguration,
                          Client retrofitClient) {
    createClient "mort", MortService, serviceConfiguration, retrofitClient
  }

  @Bean
  KatoService katoService(ServiceConfiguration serviceConfiguration,
                          Client retrofitClient) {
    createClient "kato", KatoService, serviceConfiguration, retrofitClient
  }

  //---- optional backend components:
  @Bean
  @ConditionalOnProperty('services.echo.enabled')
  EchoService echoService(ServiceConfiguration serviceConfiguration,
                          Client retrofitClient) {
    createClient "echo", EchoService, serviceConfiguration, retrofitClient
  }

  @Bean
  @ConditionalOnProperty('services.flapjack.enabled')
  FlapJackService flapJackService(ServiceConfiguration serviceConfiguration,
                                  Client retrofitClient) {
    createClient "flapjack", FlapJackService, serviceConfiguration, retrofitClient
  }

  @Bean
  @ConditionalOnProperty('services.mayo.enabled')
  MayoService mayoService(ServiceConfiguration serviceConfiguration,
                          Client retrofitClient) {
    createClient "mayo", MayoService, serviceConfiguration, retrofitClient
  }
  
  @Bean
  @ConditionalOnProperty('services.igor.enabled')
  IgorService igorService(ServiceConfiguration serviceConfiguration,
                          Client retrofitClient) {
    createClient "igor", IgorService, serviceConfiguration, retrofitClient
  }

  private static <T> T createClient(String serviceName, Class<T> type, ServiceConfiguration serviceConfiguration, Client client) {
    Service service = serviceConfiguration.getService(serviceName)
    if (service == null) {
      throw new IllegalArgumentException("Unknown service ${serviceName} requested of type ${type}")
    }
    if (!service.enabled) {
      return null
    }
    def endpoint = serviceConfiguration.discoveryHosts && service.vipAddress ?
        newFixedEndpoint("niws://${service.vipAddress}")
        : newFixedEndpoint(service.baseUrl)

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
      public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        HttpServletRequest request = (HttpServletRequest) req;
        String origin = request.getHeader("Origin") ?: "*"
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
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
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
      HystrixRequestContext.initializeContext()
      chain.doFilter(request, response)
    }

    void init(FilterConfig filterConfig) throws ServletException {}

    void destroy() {}
  }
}
