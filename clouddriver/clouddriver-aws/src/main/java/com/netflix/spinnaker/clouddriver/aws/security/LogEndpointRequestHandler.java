/*
 * Copyright 2022 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.aws.security;

import com.amazonaws.Request;
import com.amazonaws.handlers.RequestHandler2;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * A request handler that logs the endpoint that a particular client uses once per aws service name.
 * Thus it's possible to share an instance of this handler with clients of different services. Note
 * that this handler doesn't include an account name to make it possible to share clients among
 * multiple accounts. Having an account name would mean a separate handler per account and therefore
 * a separate client per account.
 */
@Slf4j
public class LogEndpointRequestHandler extends RequestHandler2 {

  /**
   * A map from service name to endpoints to track which endpoints we've seen/logged, so we only log
   * once.
   */
  private final Map<String, Set<URI>> endpoints = new HashMap<>();

  @Override
  public void beforeRequest(Request<?> request) {
    String serviceName = request.getServiceName();
    URI endpoint = request.getEndpoint();

    Set<URI> endpointsForThisService =
        endpoints.computeIfAbsent(serviceName, ignored -> new HashSet<>());
    if (!endpointsForThisService.contains(endpoint)) {
      log.info(
          "LogEndpointRequestHandler::beforeRequest: service name: '{}', endpoint: '{}'",
          serviceName,
          endpoint.toString());
      endpointsForThisService.add(endpoint);
    }
  }
}
