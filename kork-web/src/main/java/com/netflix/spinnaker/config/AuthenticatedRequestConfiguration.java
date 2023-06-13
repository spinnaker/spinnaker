/*
 * Copyright 2023 Apple Inc.
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

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.AuthenticatedRequestDecorator;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Configures hooks for propagating authenticated request headers. */
@Configuration
public class AuthenticatedRequestConfiguration {

  private static final String KEY = AuthenticatedRequestConfiguration.class.getName();

  @PostConstruct
  public void registerHook() {
    Schedulers.onScheduleHook(KEY, AuthenticatedRequestDecorator::wrap);
  }

  @PreDestroy
  public void unregisterHook() {
    Schedulers.resetOnScheduleHook(KEY);
  }

  /**
   * Configures an exchange filter function to propagate authenticated request headers into the
   * requests created by {@linkplain org.springframework.web.reactive.function.client.WebClient web
   * clients}.
   */
  @Bean
  public WebClientCustomizer authenticationHeadersPropagator() {
    return webClientBuilder ->
        webClientBuilder.filter(
            (request, next) ->
                Mono.deferContextual(
                        context -> {
                          Map<String, String> authenticationHeaders = context.get(KEY);
                          ClientRequest authenticatedRequest =
                              ClientRequest.from(request)
                                  .headers(httpHeaders -> httpHeaders.setAll(authenticationHeaders))
                                  .build();
                          return next.exchange(authenticatedRequest);
                        })
                    .contextWrite(context -> context.put(KEY, getAuthenticationHeaders())));
  }

  private static Map<String, String> getAuthenticationHeaders() {
    Map<String, String> headers = new HashMap<>();
    AuthenticatedRequest.getAuthenticationHeaders()
        .forEach(
            (headerName, value) ->
                value.ifPresent(headerValue -> headers.put(headerName, headerValue)));
    return headers;
  }
}
