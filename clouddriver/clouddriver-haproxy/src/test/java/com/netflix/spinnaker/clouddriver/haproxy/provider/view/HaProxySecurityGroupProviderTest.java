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
import com.netflix.spinnaker.clouddriver.haproxy.model.HaProxySecurityGroup;
import com.netflix.spinnaker.clouddriver.haproxy.names.HaProxyMetadataNamer;
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HaProxySecurityGroupProviderTest {

  private static final String ACCOUNT = "homelab";
  private static final String REGION = "dc1";

  private InMemoryCache cache;
  private HaProxySecurityGroupProvider provider;

  @BeforeEach
  void setup() {
    cache = new InMemoryCache();
    provider = new HaProxySecurityGroupProvider(cache, new HaProxyMetadataNamer());

    // Frontend with a src ACL gated by an http-request deny rule.
    cache.merge(
        HaProxyResourceType.FRONTEND.name(),
        new DefaultCacheData(
            HaProxyCacheKeys.frontend(ACCOUNT, REGION, "web-main"),
            Map.of(
                "name", "web-main",
                "metadata", Map.of("spinnaker-app", "web"),
                "binds",
                    Map.of(
                        "public", Map.of("address", "*", "port", 443),
                        "admin", Map.of("address", "*", "port", 8404)),
                "acl_list",
                    List.of(
                        Map.of(
                            "acl_name", "office",
                            "criterion", "src",
                            "value", "10.0.0.0/8 192.168.1.5"),
                        Map.of(
                            "acl_name", "is_health",
                            "criterion", "path",
                            "value", "/healthz")),
                "http_request_rule_list",
                    List.of(
                        Map.of("type", "deny", "cond", "unless", "cond_test", "office"),
                        Map.of("type", "set-header", "hdr_name", "X-Forwarded-Proto"))),
            Map.of()));

    // TCP-mode frontend gated by a tcp-request connection rule.
    cache.merge(
        HaProxyResourceType.FRONTEND.name(),
        new DefaultCacheData(
            HaProxyCacheKeys.frontend(ACCOUNT, REGION, "mysql-main"),
            Map.of(
                "name", "mysql-main",
                "mode", "tcp",
                "binds", Map.of("db", Map.of("address", "*", "port", 3306)),
                "acl_list",
                    List.of(
                        Map.of(
                            "acl_name", "db_clients",
                            "criterion", "src",
                            "value", "10.1.0.0/16")),
                "tcp_request_rule_list",
                    List.of(
                        Map.of(
                            "type", "connection",
                            "action", "reject",
                            "cond", "unless",
                            "cond_test", "db_clients"),
                        Map.of("type", "inspect-delay", "timeout", 5000))),
            Map.of()));

    // Frontend without any access rules: not a security group.
    cache.merge(
        HaProxyResourceType.FRONTEND.name(),
        new DefaultCacheData(
            HaProxyCacheKeys.frontend(ACCOUNT, REGION, "plain-frontend"),
            Map.of("name", "plain-frontend"),
            Map.of()));
  }

  @Test
  void tcpRequestRulesBecomeSecurityGroups() {
    HaProxySecurityGroup group = provider.get(ACCOUNT, REGION, "mysql-main", null);

    assertThat(group).isNotNull();
    assertThat(group.getInboundRules()).hasSize(1);
    IpRangeRule rule = (IpRangeRule) group.getInboundRules().iterator().next();
    assertThat(rule.getRange().getIp()).isEqualTo("10.1.0.0");
    assertThat(rule.getRange().getCidr()).isEqualTo("/16");
    assertThat(rule.getDescription()).isEqualTo("tcp-request connection reject unless db_clients");
    assertThat(rule.getPortRanges())
        .extracting(range -> range.getStartPort())
        .containsExactly(3306);
  }

  @Test
  void frontendsWithAccessRulesBecomeSecurityGroups() {
    Collection<HaProxySecurityGroup> groups = provider.getAll(true);

    assertThat(groups)
        .extracting(HaProxySecurityGroup::getName)
        .containsExactlyInAnyOrder("web-main", "mysql-main");
    HaProxySecurityGroup group =
        groups.stream().filter(g -> g.getName().equals("web-main")).findFirst().orElseThrow();
    assertThat(group.getName()).isEqualTo("web-main");
    assertThat(group.getId()).isEqualTo("web-main");
    assertThat(group.getAccountName()).isEqualTo(ACCOUNT);
    assertThat(group.getRegion()).isEqualTo(REGION);
    assertThat(group.getCloudProvider()).isEqualTo("haproxy");
    assertThat(group.getMoniker().getApp()).isEqualTo("web");
    assertThat(group.getOutboundRules()).isEmpty();

    // One rule per address in the referenced src ACL.
    assertThat(group.getInboundRules()).hasSize(2);
    assertThat(group.getInboundRules())
        .allSatisfy(
            rule -> {
              IpRangeRule ipRule = (IpRangeRule) rule;
              assertThat(ipRule.getProtocol()).isEqualTo("tcp");
              assertThat(ipRule.getDescription()).isEqualTo("http-request deny unless office");
              assertThat(ipRule.getPortRanges())
                  .extracting(range -> range.getStartPort())
                  .containsExactly(443, 8404);
            });
    assertThat(group.getInboundRules())
        .extracting(rule -> ((IpRangeRule) rule).getRange().getIp())
        .containsExactlyInAnyOrder("10.0.0.0", "192.168.1.5");
    assertThat(group.getInboundRules())
        .extracting(rule -> ((IpRangeRule) rule).getRange().getCidr())
        .containsExactlyInAnyOrder("/8", "/32");
  }

  @Test
  void filtersNarrowByAccountRegionAndName() {
    assertThat(provider.getAllByAccount(true, ACCOUNT)).hasSize(2);
    assertThat(provider.getAllByAccount(true, "other")).isEmpty();
    assertThat(provider.getAllByRegion(true, REGION)).hasSize(2);
    assertThat(provider.getAllByRegion(true, "other")).isEmpty();
    assertThat(provider.getAllByAccountAndName(true, ACCOUNT, "web-main")).hasSize(1);
    assertThat(provider.getAllByAccountAndName(true, ACCOUNT, "plain-frontend")).isEmpty();
    assertThat(provider.getAllByAccountAndRegion(true, ACCOUNT, REGION)).hasSize(2);
  }

  @Test
  void includeRulesFalseStripsRules() {
    Collection<HaProxySecurityGroup> groups = provider.getAll(false);

    assertThat(groups).hasSize(2);
    assertThat(groups).allSatisfy(group -> assertThat(group.getInboundRules()).isEmpty());
  }

  @Test
  void getReturnsTheGroupWithRulesAndNullForMisses() {
    HaProxySecurityGroup group = provider.get(ACCOUNT, REGION, "web-main", null);

    assertThat(group).isNotNull();
    assertThat(group.getInboundRules()).isNotEmpty();
    assertThat(group.getSummary().getName()).isEqualTo("web-main");

    assertThat(provider.get(ACCOUNT, REGION, "missing", null)).isNull();
    assertThat(provider.get(ACCOUNT, REGION, "plain-frontend", null)).isNull();
    assertThat(provider.getById(ACCOUNT, REGION, "web-main", null)).isNotNull();
  }
}
