/*
 * Copyright 2024 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleRegionalExternalHttpLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.util.List;
import java.util.function.Function;

public class GoogleRegionalExternalHttpLoadBalancerCachingAgent
    extends AbstractGoogleRegionalHttpLoadBalancerCachingAgent<
        GoogleRegionalExternalHttpLoadBalancer> {

  public GoogleRegionalExternalHttpLoadBalancerCachingAgent(
      String clouddriverUserAgentApplicationName,
      GoogleNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {
    super(clouddriverUserAgentApplicationName, credentials, objectMapper, registry, region);
  }

  @Override
  protected String getLoadBalancerCachingAgentName() {
    return "RegionalExternalHttpLoadBalancerCaching";
  }

  @Override
  protected GoogleRegionalExternalHttpLoadBalancer createNewLoadBalancer() {
    return new GoogleRegionalExternalHttpLoadBalancer();
  }

  @Override
  protected String getLoadBalancingSchemeFilter() {
    return "EXTERNAL";
  }

  @Override
  protected Function<GoogleRegionalExternalHttpLoadBalancer, List<GoogleBackendService>>
      getBackendServicesExtractor() {
    return lb -> Utils.getBackendServicesFromRegionalExternalHttpLoadBalancerView(lb.getView());
  }
}
