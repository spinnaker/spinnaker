/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.config

import com.netflix.spinnaker.echo.events.RestClientFactory
import com.netflix.spinnaker.echo.rest.RestService
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.codec.binary.Base64
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

import static retrofit.Endpoints.newFixedEndpoint

/**
 * Rest endpoint configuration
 */
@Slf4j
@Configuration
@ConditionalOnProperty('rest.enabled')
@CompileStatic
@SuppressWarnings('GStringExpressionWithinString')
class RestConfig {

  @Bean
  LogLevel retrofitLogLevel(@Value('${retrofit.log-level:BASIC}') String retrofitLogLevel) {
    return LogLevel.valueOf(retrofitLogLevel)
  }

  interface RequestInterceptorAttacher {
    void attach(RestAdapter.Builder builder, RequestInterceptor interceptor)
  }

  @Bean
  RequestInterceptorAttacher requestInterceptorAttacher() {
    new RequestInterceptorAttacher() {
      @Override
      public void attach(RestAdapter.Builder builder, RequestInterceptor interceptor) {
        builder.setRequestInterceptor(interceptor)
      }
    }
  }

  interface HeadersFromFile {
    Map<String, String> headers(String path)
  }

  @Bean
  HeadersFromFile headersFromFile() {
    new HeadersFromFile() {
      Map<String, String> headers(String path) {
        Map<String, String> headers = new HashMap<>()
        new File(path).eachLine { line ->
          def pair = line.split(":")
          if (pair.length == 2) {
            headers[pair[0]] = pair[1].trim()
          } else {
            log.warn("Could not parse header '$line' in '$path'")
          }
        }
        return headers
      }
    }
  }

  @Bean
  RestUrls restServices(RestProperties restProperties, RestClientFactory clientFactory, LogLevel retrofitLogLevel, RequestInterceptorAttacher requestInterceptorAttacher, HeadersFromFile headersFromFile) {

    RestUrls restUrls = new RestUrls()

    restProperties

    restProperties.endpoints.each { RestProperties.RestEndpointConfiguration endpoint ->
      RestAdapter.Builder restAdapterBuilder = new RestAdapter.Builder()
        .setEndpoint(newFixedEndpoint(endpoint.url as String))
        .setClient(clientFactory.getClient(endpoint.insecure))
        .setLogLevel(retrofitLogLevel)
        .setLog(new Slf4jRetrofitLogger(RestService.class))
        .setConverter(new JacksonConverter())

      Map<String, String> headers = new HashMap<>()

      if (endpoint.username && endpoint.password) {
        String auth = "Basic " + Base64.encodeBase64String("${endpoint.username}:${endpoint.password}".getBytes())
        headers["Authorization"] = auth
      }

      if (endpoint.headers) {
        headers += endpoint.headers
      }

      if (endpoint.headersFile) {
        headers += headersFromFile.headers(endpoint.headersFile)
      }

      if (headers) {
        RequestInterceptor headerInterceptor = new RequestInterceptor() {
          @Override
          public void intercept(RequestInterceptor.RequestFacade request) {
            headers.each { k, v ->
              request.addHeader(k, v)
            }
          }
        }
        requestInterceptorAttacher.attach(restAdapterBuilder, headerInterceptor)
      }

      restUrls.services.add(
        [
          client: restAdapterBuilder.build().create(RestService),
          config: endpoint
        ]
      )
    }

    restUrls
  }
}
