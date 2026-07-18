/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.clouddriver.haproxy.provider.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.clouddriver.haproxy.caching.HaProxyCacheKeys;
import com.netflix.spinnaker.clouddriver.haproxy.caching.HaProxyResourceType;
import com.netflix.spinnaker.clouddriver.haproxy.model.HaProxyLoadBalancer;
import com.netflix.spinnaker.clouddriver.haproxy.names.HaProxyMetadataNamer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HaProxyLoadBalancerProviderTest {

  private static final String ACCOUNT = "homelab";
  private static final String REGION = "dc1";

  private InMemoryCache cache;
  private HaProxyLoadBalancerProvider provider;

  @BeforeEach
  void setup() {
    cache = new InMemoryCache();
    provider = new HaProxyLoadBalancerProvider(cache, new HaProxyMetadataNamer());

    String frontendKey = HaProxyCacheKeys.frontend(ACCOUNT, REGION, "web-main");
    String appKey = HaProxyCacheKeys.application("web");

    cache.merge(
        HaProxyResourceType.FRONTEND.name(),
        new DefaultCacheData(
            frontendKey,
            Map.of(
                "name",
                "web-main",
                "mode",
                "http",
                "default_backend",
                "web-main-v001",
                "metadata",
                Map.of("spinnaker-app", "web"),
                "binds",
                Map.of("public", Map.of("address", "*", "port", 443)),
                "backend_switching_rule_list",
                List.of(Map.of("name", "web-main-v002", "cond", "if"))),
            Map.of(HaProxyResourceType.APPLICATION.name(), List.of(appKey))));

    cache.merge(
        HaProxyResourceType.APPLICATION.name(),
        new DefaultCacheData(
            appKey,
            Map.of("name", "web"),
            Map.of(HaProxyResourceType.FRONTEND.name(), List.of(frontendKey))));

    cache.merge(
        HaProxyResourceType.BACKEND.name(),
        new DefaultCacheData(
            HaProxyCacheKeys.backend(ACCOUNT, REGION, "web-main-v001"),
            Map.of(
                "name",
                "web-main-v001",
                "servers",
                Map.of(
                    "web001", Map.of("name", "web001", "address", "10.0.0.11", "port", 8080),
                    "web002", Map.of("name", "web002", "address", "10.0.0.12", "port", 8080))),
            Map.of()));
    // web-main-v002 is intentionally absent from the cache to cover dangling backend references.
  }

  @Test
  void applicationLoadBalancersIncludeBindsAndAttachedServerGroups() {
    Set<HaProxyLoadBalancer> loadBalancers = provider.getApplicationLoadBalancers("web");

    assertThat(loadBalancers).hasSize(1);
    HaProxyLoadBalancer lb = loadBalancers.iterator().next();
    assertThat(lb.getName()).isEqualTo("web-main");
    assertThat(lb.getAccount()).isEqualTo(ACCOUNT);
    assertThat(lb.getRegion()).isEqualTo(REGION);
    assertThat(lb.getCloudProvider()).isEqualTo("haproxy");
    assertThat(lb.getMoniker().getApp()).isEqualTo("web");
    assertThat(lb.getBinds()).containsKey("public");
    assertThat(lb.getDefaultBackend()).isEqualTo("web-main-v001");

    assertThat(lb.getServerGroups())
        .extracting(LoadBalancerServerGroup::getName)
        .containsExactlyInAnyOrder("web-main-v001", "web-main-v002");

    LoadBalancerServerGroup attached =
        lb.getServerGroups().stream()
            .filter(sg -> sg.getName().equals("web-main-v001"))
            .findFirst()
            .orElseThrow();
    assertThat(attached.getInstances()).hasSize(2);
    assertThat(attached.getInstances())
        .anySatisfy(instance -> assertThat(instance.getName()).isEqualTo("web001"));

    LoadBalancerServerGroup dangling =
        lb.getServerGroups().stream()
            .filter(sg -> sg.getName().equals("web-main-v002"))
            .findFirst()
            .orElseThrow();
    assertThat(dangling.getInstances()).isEmpty();
  }

  @Test
  void unknownApplicationsYieldNoLoadBalancers() {
    assertThat(provider.getApplicationLoadBalancers("nope")).isEmpty();
  }

  @Test
  void listGroupsByNameAccountAndRegion() {
    List<HaProxyLoadBalancerProvider.HaProxyLoadBalancerSummary> summaries = provider.list();

    assertThat(summaries).hasSize(1);
    HaProxyLoadBalancerProvider.HaProxyLoadBalancerSummary summary = summaries.get(0);
    assertThat(summary.getName()).isEqualTo("web-main");
    assertThat(summary.getByAccounts()).hasSize(1);
    assertThat(summary.getByAccounts().get(0).getName()).isEqualTo(ACCOUNT);
    assertThat(summary.getByAccounts().get(0).getByRegions().get(0).getName()).isEqualTo(REGION);
    assertThat(
            summary
                .getByAccounts()
                .get(0)
                .getByRegions()
                .get(0)
                .getLoadBalancers()
                .get(0)
                .getName())
        .isEqualTo("web-main");

    assertThat(provider.get("web-main")).isNotNull();
    assertThat(provider.get("missing")).isNull();
  }

  @Test
  void byAccountAndRegionAndNameReturnsTheDetail() {
    List<HaProxyLoadBalancerProvider.HaProxyLoadBalancerDetail> details =
        provider.byAccountAndRegionAndName(ACCOUNT, REGION, "web-main");

    assertThat(details).hasSize(1);
    assertThat(details.get(0).getName()).isEqualTo("web-main");
    assertThat(details.get(0).getAccount()).isEqualTo(ACCOUNT);
    assertThat(details.get(0).getRegion()).isEqualTo(REGION);
    assertThat(details.get(0).getType()).isEqualTo("haproxy");

    assertThat(provider.byAccountAndRegionAndName(ACCOUNT, REGION, "missing")).isEmpty();
  }
}
