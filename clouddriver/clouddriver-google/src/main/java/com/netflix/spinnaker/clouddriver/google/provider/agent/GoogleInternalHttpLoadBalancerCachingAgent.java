package com.netflix.spinnaker.clouddriver.google.provider.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalHttpLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.util.List;
import java.util.function.Function;

public class GoogleInternalHttpLoadBalancerCachingAgent
    extends AbstractGoogleRegionalHttpLoadBalancerCachingAgent<GoogleInternalHttpLoadBalancer> {

  public GoogleInternalHttpLoadBalancerCachingAgent(
      String clouddriverUserAgentApplicationName,
      GoogleNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {
    super(clouddriverUserAgentApplicationName, credentials, objectMapper, registry, region);
  }

  @Override
  protected String getLoadBalancerCachingAgentName() {
    return "InternalHttpLoadBalancerCaching";
  }

  @Override
  protected GoogleInternalHttpLoadBalancer createNewLoadBalancer() {
    return new GoogleInternalHttpLoadBalancer();
  }

  @Override
  protected String getLoadBalancingSchemeFilter() {
    return "INTERNAL_MANAGED";
  }

  @Override
  protected Function<GoogleInternalHttpLoadBalancer, List<GoogleBackendService>>
      getBackendServicesExtractor() {
    return lb -> Utils.getBackendServicesFromInternalHttpLoadBalancerView(lb.getView());
  }
}
