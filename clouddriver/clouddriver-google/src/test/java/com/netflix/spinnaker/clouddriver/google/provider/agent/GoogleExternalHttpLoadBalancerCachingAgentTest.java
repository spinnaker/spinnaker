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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Backend;
import com.google.api.services.compute.model.BackendService;
import com.google.api.services.compute.model.ForwardingRule;
import com.google.api.services.compute.model.TargetHttpsProxy;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleExternalHttpLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class GoogleExternalHttpLoadBalancerCachingAgentTest {
  private static final String ACCOUNT = "auto";
  private static final String PROJECT = "my-project";
  private static final String REGION = "us-central1";

  @Test
  void isExternalManagedHttpForwardingRule_acceptsOnlyExternalManagedHttpProxies() {
    ForwardingRule externalManagedHttp =
        new ForwardingRule()
            .setLoadBalancingScheme("EXTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetHttpProxies/external-proxy");
    ForwardingRule externalManagedHttps =
        new ForwardingRule()
            .setLoadBalancingScheme("EXTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetHttpsProxies/external-proxy");
    ForwardingRule internalManagedHttp =
        new ForwardingRule()
            .setLoadBalancingScheme("INTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetHttpProxies/internal-proxy");
    ForwardingRule externalManagedSsl =
        new ForwardingRule()
            .setLoadBalancingScheme("EXTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetSslProxies/ssl-proxy");
    ForwardingRule missingTarget = new ForwardingRule().setLoadBalancingScheme("EXTERNAL_MANAGED");

    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.isExternalManagedHttpForwardingRule(
                externalManagedHttp))
        .isTrue();
    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.isExternalManagedHttpForwardingRule(
                externalManagedHttps))
        .isTrue();
    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.isExternalManagedHttpForwardingRule(
                internalManagedHttp))
        .isFalse();
    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.isExternalManagedHttpForwardingRule(
                externalManagedSsl))
        .isFalse();
    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.isExternalManagedHttpForwardingRule(
                missingTarget))
        .isFalse();
  }

  @Test
  void fetchAndApplyBackendHealth_toleratesBackendHealthFailures() throws IOException {
    Compute compute = mock(Compute.class);
    Compute.RegionBackendServices regionBackendServices = mock(Compute.RegionBackendServices.class);
    Compute.RegionBackendServices.GetHealth getHealth =
        mock(Compute.RegionBackendServices.GetHealth.class);
    when(compute.regionBackendServices()).thenReturn(regionBackendServices);
    when(regionBackendServices.getHealth(eq(PROJECT), eq(REGION), eq("backend-service"), any()))
        .thenReturn(getHealth);
    when(getHealth.execute()).thenThrow(new IOException("boom"));

    GoogleNamedAccountCredentials credentials =
        new GoogleNamedAccountCredentials.Builder()
            .name(ACCOUNT)
            .project(PROJECT)
            .compute(compute)
            .credentials(mock(GoogleCredentials.class))
            .build();
    GoogleExternalHttpLoadBalancerCachingAgent agent =
        new GoogleExternalHttpLoadBalancerCachingAgent(
            "clouddriver", credentials, new ObjectMapper(), new DefaultRegistry(), REGION);
    GoogleExternalHttpLoadBalancer loadBalancer = new GoogleExternalHttpLoadBalancer();
    loadBalancer.setName("external-lb");
    loadBalancer.setHealths(new ArrayList<>());
    BackendService backendService = new BackendService().setName("backend-service");
    Backend backend =
        new Backend()
            .setGroup(
                "https://compute.googleapis.com/compute/v1/projects/"
                    + PROJECT
                    + "/zones/us-central1-a/instanceGroups/server-group");

    assertDoesNotThrow(
        () -> agent.fetchAndApplyBackendHealth(loadBalancer, backendService, backend));
    assertThat(loadBalancer.getHealths()).isEmpty();
  }

  @Test
  void getFirstSslCertificateForExternalManaged_preservesCertificateManagerResourceIdentity() {
    String certificateManagerCertificate =
        "//certificatemanager.googleapis.com/projects/"
            + PROJECT
            + "/locations/"
            + REGION
            + "/certificates/shared-name";
    String computeCertificate =
        "https://compute.googleapis.com/compute/v1/projects/"
            + PROJECT
            + "/regions/"
            + REGION
            + "/sslCertificates/shared-name";

    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.getFirstSslCertificateForExternalManaged(
                new TargetHttpsProxy().setSslCertificates(new ArrayList<>())))
        .isNull();
    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.getFirstSslCertificateForExternalManaged(
                new TargetHttpsProxy().setSslCertificates(List.of(certificateManagerCertificate))))
        .isEqualTo(certificateManagerCertificate);
    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.getFirstSslCertificateForExternalManaged(
                new TargetHttpsProxy().setSslCertificates(List.of(computeCertificate))))
        .isEqualTo("shared-name");
  }
}
