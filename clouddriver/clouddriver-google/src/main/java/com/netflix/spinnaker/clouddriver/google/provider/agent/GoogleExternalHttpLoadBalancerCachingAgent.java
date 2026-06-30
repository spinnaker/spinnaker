/*
 * Copyright 2026 Harness, Inc.
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
import com.google.api.services.compute.model.ForwardingRule;
import com.google.api.services.compute.model.TargetHttpsProxy;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleExternalHttpLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHostRule;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caches regional external Application Load Balancers backed by the Compute `EXTERNAL_MANAGED`
 * resource graph.
 *
 * <p>GCP models each listener as a regional forwarding rule. Spinnaker exposes the URL map as the
 * logical load balancer, so the shared regional HTTP base walks forwarding rule -> regional target
 * proxy -> regional URL map -> regional backend services and health checks.
 */
public class GoogleExternalHttpLoadBalancerCachingAgent
    extends AbstractGoogleRegionalHttpLoadBalancerCachingAgent<GoogleExternalHttpLoadBalancer> {
  private static final Logger log = LoggerFactory.getLogger(GoogleExternalHttpLoadBalancer.class);

  public GoogleExternalHttpLoadBalancerCachingAgent(
      String clouddriverUserAgentApplicationName,
      GoogleNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {
    super(clouddriverUserAgentApplicationName, credentials, objectMapper, registry, region);
  }

  static boolean isExternalManagedHttpForwardingRule(ForwardingRule forwardingRule) {
    return GoogleLoadBalancerCacheSupport.isRegionalManagedHttpForwardingRule(
        forwardingRule, "EXTERNAL_MANAGED");
  }

  static String getFirstSslCertificateForExternalManaged(TargetHttpsProxy targetHttpsProxy) {
    if (targetHttpsProxy == null
        || targetHttpsProxy.getSslCertificates() == null
        || targetHttpsProxy.getSslCertificates().isEmpty()) {
      return null;
    }
    String certificate = targetHttpsProxy.getSslCertificates().get(0);
    String certificateManagerCertificate =
        GCEUtil.normalizeRegionalCertificateManagerCertificate(certificate);
    return certificateManagerCertificate != null
        ? certificateManagerCertificate
        : GCEUtil.getLocalName(certificate);
  }

  @Override
  protected String getInstrumentationPrefix() {
    return "ExternalHttpLoadBalancerCaching";
  }

  @Override
  protected boolean isOwnedForwardingRule(ForwardingRule forwardingRule) {
    return isExternalManagedHttpForwardingRule(forwardingRule);
  }

  @Override
  protected GoogleExternalHttpLoadBalancer newLoadBalancer(ForwardingRule forwardingRule) {
    GoogleExternalHttpLoadBalancer loadBalancer = new GoogleExternalHttpLoadBalancer();
    populateCommonFields(loadBalancer, forwardingRule);
    loadBalancer.setNetwork(forwardingRule.getNetwork());
    loadBalancer.setHostRules(new ArrayList<>());
    return loadBalancer;
  }

  @Override
  protected void applyHttpsProxyFields(
      GoogleExternalHttpLoadBalancer loadBalancer, TargetHttpsProxy targetHttpsProxy) {
    loadBalancer.setCertificate(getFirstSslCertificateForExternalManaged(targetHttpsProxy));
  }

  @Override
  protected void setUrlMapName(GoogleExternalHttpLoadBalancer loadBalancer, String urlMapName) {
    loadBalancer.setUrlMapName(urlMapName);
  }

  @Override
  protected void setDefaultService(
      GoogleExternalHttpLoadBalancer loadBalancer, GoogleBackendService defaultService) {
    loadBalancer.setDefaultService(defaultService);
  }

  @Override
  protected List<GoogleHostRule> getHostRules(GoogleExternalHttpLoadBalancer loadBalancer) {
    return loadBalancer.getHostRules();
  }

  @Override
  protected List<GoogleBackendService> getBackendServicesFromView(
      GoogleExternalHttpLoadBalancer loadBalancer) {
    return Utils.getBackendServicesFromExternalHttpLoadBalancerView(loadBalancer.getView());
  }

  @Override
  protected void handleMissingBackendService(
      String backendServiceName, GoogleExternalHttpLoadBalancer loadBalancer) {
    // Preserve the load balancer and let the next successful backend-service read enrich it.
    log.warn(
        "Could not enrich regional external HTTP load balancer {} because backend service {} was missing.",
        loadBalancer.getName(),
        backendServiceName);
  }

  @Override
  protected void handleUnsupportedTargetProxy(
      ForwardingRule forwardingRule,
      GoogleExternalHttpLoadBalancer loadBalancer,
      List<String> failedLoadBalancers) {
    failedLoadBalancers.add(loadBalancer.getName());
    log.debug(
        "Unsupported target proxy for regional external HTTP load balancer {}",
        forwardingRule.getName());
  }

  @Override
  protected String getWrongSchemeMessage() {
    return "Not responsible for on demand caching of load balancers without "
        + GoogleLoadBalancerType.EXTERNAL_MANAGED
        + " HTTP(S) target proxy.";
  }
}
