/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

/**
 * An AWS SDK v2 {@link ExecutionInterceptor} that logs the endpoint a particular client uses, once
 * per AWS service name. A single instance can be shared across clients for different services;
 * shared across clients for different accounts too, since it logs by service name only, not
 * account.
 */
@Slf4j
public class LogEndpointExecutionInterceptor implements ExecutionInterceptor {

  /**
   * A map from service name to endpoints to track which endpoints we've seen/logged, so we only log
   * once.
   */
  private final Map<String, Set<URI>> endpoints = new ConcurrentHashMap<>();

  @Override
  public void beforeTransmission(
      Context.BeforeTransmission context, ExecutionAttributes executionAttributes) {
    String serviceName = executionAttributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    URI endpoint = context.httpRequest().getUri();

    Set<URI> endpointsForThisService =
        endpoints.computeIfAbsent(serviceName, ignored -> ConcurrentHashMap.newKeySet());
    if (endpointsForThisService.add(endpoint)) {
      log.info(
          "LogEndpointExecutionInterceptor::beforeTransmission: service name: '{}', endpoint: '{}'",
          serviceName,
          endpoint);
    }
  }
}
