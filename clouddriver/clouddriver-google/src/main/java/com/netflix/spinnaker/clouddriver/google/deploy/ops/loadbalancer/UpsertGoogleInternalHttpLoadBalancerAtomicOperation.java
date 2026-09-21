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
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.google.model.GoogleNetwork;
import com.netflix.spinnaker.clouddriver.google.model.GoogleSubnet;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalHttpLoadBalancer;
import java.util.List;
import org.codehaus.groovy.runtime.StringGroovyMethods;

/** Internal-managed specialization of the shared regional HTTP(S) upsert operation. */
public class UpsertGoogleInternalHttpLoadBalancerAtomicOperation
    extends AbstractUpsertGoogleRegionalHttpLoadBalancerAtomicOperation {
  public UpsertGoogleInternalHttpLoadBalancerAtomicOperation(
      UpsertGoogleLoadBalancerDescription description) {
    super(description);
  }

  @Override
  protected String getBasePhase() {
    return "UPSERT_INTERNAL_HTTP_LOAD_BALANCER";
  }

  @Override
  protected String getLoadBalancerDescriptionLabel() {
    return "Internal HTTP load balancer";
  }

  @Override
  protected GoogleSubnet resolveSubnet(GoogleNetwork network) {
    return GCEUtil.querySubnet(
        description.getAccountName(),
        description.getRegion(),
        description.getSubnet(),
        TaskRepository.threadLocalTask.get(),
        getBasePhase(),
        googleSubnetProvider);
  }

  @Override
  protected void configureLoadBalancerNetwork(
      GoogleInternalHttpLoadBalancer loadBalancer, GoogleNetwork network, GoogleSubnet subnet) {
    loadBalancer.setNetwork(network.getSelfLink());
    loadBalancer.setSubnet(subnet.getSelfLink());
  }

  @Override
  protected List<GoogleBackendService> getBackendServicesFromLoadBalancer(
      GoogleInternalHttpLoadBalancer loadBalancer) {
    return Utils.getBackendServicesFromInternalHttpLoadBalancerView(loadBalancer.getView());
  }

  @Override
  protected String getLoadBalancingScheme() {
    return "INTERNAL_MANAGED";
  }

  @Override
  protected boolean managesBackendProtocol() {
    return false;
  }

  @Override
  protected String buildCertificateUrl(String project, String region, String certificate) {
    return GCEUtil.buildRegionalCertificateUrl(project, region, certificate);
  }

  @Override
  protected String getExistingCertificateForComparison(List<String> sslCertificates) {
    return getFirstSslCertificateName(sslCertificates);
  }

  @Override
  protected String getDesiredCertificateForComparison(
      String project, String region, String certificate) {
    return GCEUtil.getLocalName(buildCertificateUrl(project, region, certificate));
  }

  @Override
  protected void configureForwardingRule(
      ForwardingRule rule, GoogleInternalHttpLoadBalancer loadBalancer, String targetProxyUrl) {
    rule.setName(loadBalancer.getName());
    rule.setLoadBalancingScheme(getLoadBalancingScheme());
    rule.setIPAddress(loadBalancer.getIpAddress());
    rule.setIPProtocol(loadBalancer.getIpProtocol());
    rule.setNetwork(loadBalancer.getNetwork());
    rule.setSubnetwork(loadBalancer.getSubnet());
    rule.setPortRange(
        StringGroovyMethods.asBoolean(loadBalancer.getCertificate())
            ? "443"
            : loadBalancer.getPortRange());
    rule.setTarget(targetProxyUrl);
  }
}
