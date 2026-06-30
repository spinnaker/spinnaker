package com.netflix.spinnaker.clouddriver.google.provider.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.compute.model.ForwardingRule;
import com.google.api.services.compute.model.HealthCheck;
import com.google.api.services.compute.model.TargetHttpsProxy;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHostRule;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalHttpLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleInternalHttpLoadBalancerCachingAgent
    extends AbstractGoogleRegionalHttpLoadBalancerCachingAgent<GoogleInternalHttpLoadBalancer> {
  private static final Logger log = LoggerFactory.getLogger(GoogleInternalHttpLoadBalancer.class);

  public GoogleInternalHttpLoadBalancerCachingAgent(
      String clouddriverUserAgentApplicationName,
      GoogleNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {
    super(clouddriverUserAgentApplicationName, credentials, objectMapper, registry, region);
  }

  static String getFirstSslCertificateName(TargetHttpsProxy targetHttpsProxy) {
    List<String> sslCertificates = targetHttpsProxy.getSslCertificates();
    return sslCertificates != null && !sslCertificates.isEmpty()
        ? Utils.getLocalName(sslCertificates.get(0))
        : null;
  }

  static boolean isInternalManagedHttpForwardingRule(ForwardingRule forwardingRule) {
    return GoogleLoadBalancerCacheSupport.isRegionalManagedHttpForwardingRule(
        forwardingRule, "INTERNAL_MANAGED");
  }

  static void handleHealthCheck(
      final HealthCheck healthCheck, List<GoogleBackendService> googleBackendServices) {
    AbstractGoogleRegionalHttpLoadBalancerCachingAgent.handleHealthCheck(
        healthCheck, googleBackendServices);
  }

  @Override
  protected String getInstrumentationPrefix() {
    return "InternalHttpLoadBalancerCaching";
  }

  @Override
  protected boolean isOwnedForwardingRule(ForwardingRule forwardingRule) {
    return isInternalManagedHttpForwardingRule(forwardingRule);
  }

  @Override
  protected GoogleInternalHttpLoadBalancer newLoadBalancer(ForwardingRule forwardingRule) {
    GoogleInternalHttpLoadBalancer loadBalancer = new GoogleInternalHttpLoadBalancer();
    populateCommonFields(loadBalancer, forwardingRule);
    loadBalancer.setNetwork(forwardingRule.getNetwork());
    loadBalancer.setSubnet(forwardingRule.getSubnetwork());
    loadBalancer.setHostRules(new ArrayList<>());
    return loadBalancer;
  }

  @Override
  protected void applyHttpsProxyFields(
      GoogleInternalHttpLoadBalancer loadBalancer, TargetHttpsProxy targetHttpsProxy) {
    // sslCertificates may be unset when the proxy is configured with certificateMap.
    loadBalancer.setCertificate(getFirstSslCertificateName(targetHttpsProxy));
    loadBalancer.setCertificateMap(Utils.getLocalName(targetHttpsProxy.getCertificateMap()));
  }

  @Override
  protected void setUrlMapName(GoogleInternalHttpLoadBalancer loadBalancer, String urlMapName) {
    loadBalancer.setUrlMapName(urlMapName);
  }

  @Override
  protected void setDefaultService(
      GoogleInternalHttpLoadBalancer loadBalancer, GoogleBackendService defaultService) {
    loadBalancer.setDefaultService(defaultService);
  }

  @Override
  protected List<GoogleHostRule> getHostRules(GoogleInternalHttpLoadBalancer loadBalancer) {
    return loadBalancer.getHostRules();
  }

  @Override
  protected List<GoogleBackendService> getBackendServicesFromView(
      GoogleInternalHttpLoadBalancer loadBalancer) {
    return Utils.getBackendServicesFromInternalHttpLoadBalancerView(loadBalancer.getView());
  }

  @Override
  protected void handleMissingBackendService(
      String backendServiceName, GoogleInternalHttpLoadBalancer loadBalancer) {
    throw new NoSuchElementException(
        "Could not find regional backend service "
            + backendServiceName
            + " for "
            + loadBalancer.getName());
  }

  @Override
  protected void handleUnsupportedTargetProxy(
      ForwardingRule forwardingRule,
      GoogleInternalHttpLoadBalancer loadBalancer,
      List<String> failedLoadBalancers) {
    log.debug("Non-Http target type found for global forwarding rule {}", forwardingRule.getName());
  }

  @Override
  protected String getWrongSchemeMessage() {
    return "Not responsible for on demand caching of load balancers without "
        + GoogleLoadBalancerType.INTERNAL_MANAGED
        + " HTTP(S) target proxy.";
  }
}
