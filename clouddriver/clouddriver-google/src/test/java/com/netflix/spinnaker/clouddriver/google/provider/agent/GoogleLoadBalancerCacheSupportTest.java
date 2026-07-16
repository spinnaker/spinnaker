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

import com.google.api.services.compute.model.ForwardingRule;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GoogleLoadBalancerCacheSupportTest {

  @Test
  void isRegionalManagedHttpForwardingRuleAcceptsOnlyMatchingRegionalHttpProxyRules() {
    assertThat(
            GoogleLoadBalancerCacheSupport.isRegionalManagedHttpForwardingRule(
                managedRule("INTERNAL_MANAGED", "targetHttpProxies/internal-proxy"),
                "INTERNAL_MANAGED"))
        .isTrue();
    assertThat(
            GoogleLoadBalancerCacheSupport.isRegionalManagedHttpForwardingRule(
                managedRule("EXTERNAL_MANAGED", "targetHttpsProxies/external-proxy"),
                "EXTERNAL_MANAGED"))
        .isTrue();
    assertThat(
            GoogleLoadBalancerCacheSupport.isRegionalManagedHttpForwardingRule(
                managedRule("EXTERNAL_MANAGED", "targetSslProxies/ssl-proxy"), "EXTERNAL_MANAGED"))
        .isFalse();
    assertThat(
            GoogleLoadBalancerCacheSupport.isRegionalManagedHttpForwardingRule(
                managedRule("INTERNAL_MANAGED", "targetHttpProxies/internal-proxy"),
                "EXTERNAL_MANAGED"))
        .isFalse();
    assertThat(
            GoogleLoadBalancerCacheSupport.isRegionalManagedHttpForwardingRule(
                new ForwardingRule().setLoadBalancingScheme("EXTERNAL_MANAGED"),
                "EXTERNAL_MANAGED"))
        .isFalse();
  }

  @Test
  void isRegionalPassthroughForwardingRuleAcceptsOnlyBackendServiceRulesForSchemeAndProtocol() {
    assertThat(
            GoogleLoadBalancerCacheSupport.isRegionalPassthroughForwardingRule(
                passthroughRule("EXTERNAL", "TCP"), "EXTERNAL", Set.of("TCP", "UDP")))
        .isTrue();
    assertThat(
            GoogleLoadBalancerCacheSupport.isRegionalPassthroughForwardingRule(
                passthroughRule("EXTERNAL", "UDP"), "EXTERNAL", Set.of("TCP", "UDP")))
        .isTrue();
    assertThat(
            GoogleLoadBalancerCacheSupport.isRegionalPassthroughForwardingRule(
                passthroughRule("INTERNAL", "TCP"), "INTERNAL", null))
        .isTrue();
    assertThat(
            GoogleLoadBalancerCacheSupport.isRegionalPassthroughForwardingRule(
                passthroughRule("EXTERNAL", "ESP"), "EXTERNAL", Set.of("TCP", "UDP")))
        .isFalse();
    assertThat(
            GoogleLoadBalancerCacheSupport.isRegionalPassthroughForwardingRule(
                passthroughRule("EXTERNAL", "TCP").setTarget("targetPools/pool"),
                "EXTERNAL",
                Set.of("TCP", "UDP")))
        .isFalse();
    assertThat(
            GoogleLoadBalancerCacheSupport.isRegionalPassthroughForwardingRule(
                new ForwardingRule().setLoadBalancingScheme("EXTERNAL").setIPProtocol("TCP"),
                "EXTERNAL",
                Set.of("TCP", "UDP")))
        .isFalse();
  }

  private static ForwardingRule managedRule(String scheme, String targetTypeAndName) {
    return new ForwardingRule()
        .setLoadBalancingScheme(scheme)
        .setTarget("projects/test/regions/us-central1/" + targetTypeAndName);
  }

  private static ForwardingRule passthroughRule(String scheme, String protocol) {
    return new ForwardingRule()
        .setLoadBalancingScheme(scheme)
        .setBackendService("projects/test/regions/us-central1/backendServices/backend-service")
        .setIPProtocol(protocol);
  }
}
