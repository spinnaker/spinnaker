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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer;

import com.google.api.services.compute.model.ForwardingRule;
import com.netflix.spinnaker.clouddriver.google.cache.Keys;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.google.model.GoogleNetwork;
import com.netflix.spinnaker.clouddriver.google.model.GoogleSubnet;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleExternalHttpLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalHttpLoadBalancer;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Upserts regional external Application Load Balancers using the regional HTTP operation shape.
 *
 * <p>`EXTERNAL_MANAGED` uses the same regional URL map, target proxy, backend service, and health
 * check graph as `INTERNAL_MANAGED`, but its forwarding rule is network-scoped, must not set a
 * subnetwork, requires a proxy-only subnet to already exist in the selected VPC/region, and may
 * attach regional Certificate Manager certificate resources through `sslCertificates`.
 */
public class UpsertGoogleExternalHttpLoadBalancerAtomicOperation
    extends UpsertGoogleInternalHttpLoadBalancerAtomicOperation {
  static final String PROXY_ONLY_SUBNET_PURPOSE = "REGIONAL_MANAGED_PROXY";

  public UpsertGoogleExternalHttpLoadBalancerAtomicOperation(
      UpsertGoogleLoadBalancerDescription description) {
    super(description);
  }

  @Override
  protected String getBasePhase() {
    return "UPSERT_EXTERNAL_HTTP_LOAD_BALANCER";
  }

  @Override
  protected String getLoadBalancerDescriptionLabel() {
    return "Regional External HTTP(S) load balancer";
  }

  @Override
  public Map operate(List priorOutputs) {
    if (description.getCertificateMap() != null) {
      throw new IllegalArgumentException("certificateMap is not supported for EXTERNAL_MANAGED.");
    }
    return super.operate(priorOutputs);
  }

  @Override
  protected GoogleSubnet resolveSubnet(GoogleNetwork network) {
    // EXTERNAL_MANAGED forwarding rules attach to the VPC network rather than a user subnet, but
    // the regional external Application Load Balancer still requires a REGIONAL_MANAGED_PROXY
    // subnet in that VPC and region for Google-managed proxies.
    Set<GoogleSubnet> subnets =
        googleSubnetProvider.getAllMatchingKeyPattern(
            Keys.getSubnetKey("*", description.getRegion(), description.getAccountName()));
    if (subnets == null) {
      subnets = Set.of();
    }
    boolean hasProxyOnlySubnet =
        subnets.stream()
            .anyMatch(
                subnet ->
                    PROXY_ONLY_SUBNET_PURPOSE.equals(subnet.getPurpose())
                        && isSameNetwork(subnet, network));
    if (!hasProxyOnlySubnet) {
      throw new IllegalArgumentException(
          "A proxy-only subnet with purpose "
              + PROXY_ONLY_SUBNET_PURPOSE
              + " must exist in "
              + description.getRegion()
              + " for network "
              + description.getNetwork()
              + " before creating an EXTERNAL_MANAGED HTTP(S) load balancer.");
    }
    // Returning null is intentional: the parent regional HTTP operation treats a non-null subnet as
    // a forwarding-rule subnetwork, which EXTERNAL_MANAGED forwarding rules must not set.
    return null;
  }

  @Override
  protected void configureLoadBalancerNetwork(
      GoogleInternalHttpLoadBalancer loadBalancer, GoogleNetwork network, GoogleSubnet subnet) {
    loadBalancer.setNetwork(network.getSelfLink());
  }

  @Override
  protected List<GoogleBackendService> getBackendServicesFromLoadBalancer(
      GoogleInternalHttpLoadBalancer loadBalancer) {
    GoogleExternalHttpLoadBalancer externalLoadBalancer = new GoogleExternalHttpLoadBalancer();
    externalLoadBalancer.setName(loadBalancer.getName());
    externalLoadBalancer.setDefaultService(loadBalancer.getDefaultService());
    externalLoadBalancer.setHostRules(loadBalancer.getHostRules());
    externalLoadBalancer.setCertificate(loadBalancer.getCertificate());
    externalLoadBalancer.setIpAddress(loadBalancer.getIpAddress());
    externalLoadBalancer.setIpProtocol(loadBalancer.getIpProtocol());
    externalLoadBalancer.setPortRange(loadBalancer.getPortRange());
    externalLoadBalancer.setNetwork(loadBalancer.getNetwork());
    return Utils.getBackendServicesFromExternalHttpLoadBalancerView(externalLoadBalancer.getView());
  }

  @Override
  protected String getLoadBalancingScheme() {
    return "EXTERNAL_MANAGED";
  }

  @Override
  protected String buildCertificateUrl(String project, String region, String certificate) {
    // Regional target HTTPS proxies use sslCertificates for both regional Compute SSL certificates
    // and Certificate Manager certificates. Certificate Manager resources must keep their API
    // namespace; otherwise they are indistinguishable from plain Compute SSL certificate names.
    String certificateManagerCertificate =
        GCEUtil.normalizeRegionalCertificateManagerCertificate(certificate);
    if (certificateManagerCertificate != null) {
      return certificateManagerCertificate;
    }
    if (looksLikeCertificateManagerCertificate(certificate)) {
      throw new IllegalArgumentException(
          "Certificate Manager certificates must use projects/{project}/locations/{region}/certificates/{name} "
              + "or //certificatemanager.googleapis.com/projects/{project}/locations/{region}/certificates/{name}.");
    }
    return GCEUtil.buildRegionalCertificateUrl(project, region, certificate);
  }

  private boolean looksLikeCertificateManagerCertificate(String certificate) {
    return certificate != null
        && (certificate.contains("certificatemanager.googleapis.com")
            || (certificate.contains("/locations/") && certificate.contains("/certificates")));
  }

  @Override
  protected String getExistingCertificateForComparison(List<String> sslCertificates) {
    if (sslCertificates == null || sslCertificates.isEmpty()) {
      return null;
    }
    return normalizeExternalManagedCertificateForComparison(sslCertificates.get(0));
  }

  @Override
  protected String getDesiredCertificateForComparison(
      String project, String region, String certificate) {
    return normalizeExternalManagedCertificateForComparison(
        buildCertificateUrl(project, region, certificate));
  }

  private String normalizeExternalManagedCertificateForComparison(Object certificate) {
    String certificateValue = certificate != null ? String.valueOf(certificate) : null;
    String certificateManagerCertificate =
        GCEUtil.normalizeRegionalCertificateManagerCertificate(certificateValue);
    return certificateManagerCertificate != null
        ? certificateManagerCertificate
        : GCEUtil.getLocalName(certificateValue);
  }

  @Override
  protected void configureForwardingRule(
      ForwardingRule rule, GoogleInternalHttpLoadBalancer loadBalancer, String targetProxyUrl) {
    super.configureForwardingRule(rule, loadBalancer, targetProxyUrl);
    rule.setSubnetwork(null);
    rule.setIPProtocol("TCP");
    rule.setNetworkTier(description.getNetworkTier());
  }

  private boolean isSameNetwork(GoogleSubnet subnet, GoogleNetwork network) {
    // GoogleSubnetProvider.deriveNetworkId stores a cached subnet's network as a bare local name
    // (e.g. "default") for the account's own project and as "<project>/<name>" for XPN host-project
    // subnets, never as a full "projects/.../networks/..." URL. Match on the local network name
    // and,
    // when both sides expose a project, require the projects to agree. Comparing projects keeps a
    // same-named network in another XPN project from being wrongly accepted, while a bare local
    // name
    // (no project) is treated as belonging to the selected network's project.
    String subnetNetwork = subnet.getNetwork();
    if (subnetNetwork == null) {
      return false;
    }
    String selectedName =
        network.getName() != null ? network.getName() : GCEUtil.getLocalName(network.getSelfLink());
    String subnetName = GCEUtil.getLocalName(subnetNetwork);
    if (selectedName == null || subnetName == null || !subnetName.equals(selectedName)) {
      return false;
    }
    String subnetProject = networkProject(subnetNetwork);
    String selectedProject = networkProject(network.getSelfLink());
    return subnetProject == null
        || selectedProject == null
        || subnetProject.equals(selectedProject);
  }

  private String networkProject(String networkReference) {
    if (networkReference == null) {
      return null;
    }
    if (networkReference.contains("projects/")) {
      return GCEUtil.deriveProjectId(networkReference);
    }
    int lastSlash = networkReference.lastIndexOf('/');
    if (lastSlash > 0) {
      // "<project>/<name>" shape produced for XPN host-project subnets.
      return networkReference.substring(0, lastSlash);
    }
    // Bare local name: the project is implicitly the account's/selected network's project.
    return null;
  }
}
