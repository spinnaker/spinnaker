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

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.haproxy.HaProxyProvider;
import com.netflix.spinnaker.clouddriver.haproxy.caching.HaProxyCacheKeys;
import com.netflix.spinnaker.clouddriver.haproxy.caching.HaProxyResourceType;
import com.netflix.spinnaker.clouddriver.haproxy.model.HaProxySecurityGroup;
import com.netflix.spinnaker.clouddriver.haproxy.names.HaProxyMetadataNamer;
import com.netflix.spinnaker.clouddriver.haproxy.names.HaProxyResource;
import com.netflix.spinnaker.clouddriver.model.AddressableRange;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider;
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule;
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Presents the client-access policy of cached HAProxy frontends as security groups.
 *
 * <p>A frontend yields a security group (named after the frontend) when it declares {@code src}
 * ACLs referenced by {@code http-request allow/deny} rules. Each referenced ACL value expands to
 * one {@link IpRangeRule} per address, with the frontend's bind ports as the port ranges and the
 * rule's {@code type/cond/cond_test} recorded in the description. Frontends without such rules
 * produce no security group.
 */
@Component
public class HaProxySecurityGroupProvider implements SecurityGroupProvider<HaProxySecurityGroup> {

  private final Cache cacheView;
  private final HaProxyMetadataNamer namer;

  public HaProxySecurityGroupProvider(Cache cacheView, HaProxyMetadataNamer namer) {
    this.cacheView = cacheView;
    this.namer = namer;
  }

  @Override
  public String getCloudProvider() {
    return HaProxyProvider.ID;
  }

  @Override
  public Collection<HaProxySecurityGroup> getAll(boolean includeRules) {
    return fromFrontends(cacheView.getAll(HaProxyResourceType.FRONTEND.name()), includeRules);
  }

  @Override
  public Collection<HaProxySecurityGroup> getAllByRegion(boolean includeRules, String region) {
    return getAll(includeRules).stream()
        .filter(group -> region.equals(group.getRegion()))
        .collect(Collectors.toList());
  }

  @Override
  public Collection<HaProxySecurityGroup> getAllByAccount(boolean includeRules, String account) {
    return getAll(includeRules).stream()
        .filter(group -> account.equals(group.getAccountName()))
        .collect(Collectors.toList());
  }

  @Override
  public Collection<HaProxySecurityGroup> getAllByAccountAndName(
      boolean includeRules, String account, String name) {
    return getAllByAccount(includeRules, account).stream()
        .filter(group -> name.equals(group.getName()))
        .collect(Collectors.toList());
  }

  @Override
  public Collection<HaProxySecurityGroup> getAllByAccountAndRegion(
      boolean includeRules, String account, String region) {
    return getAllByAccount(includeRules, account).stream()
        .filter(group -> region.equals(group.getRegion()))
        .collect(Collectors.toList());
  }

  @Override
  public HaProxySecurityGroup get(String account, String region, String name, String vpcId) {
    CacheData frontend =
        cacheView.get(
            HaProxyResourceType.FRONTEND.name(), HaProxyCacheKeys.frontend(account, region, name));
    if (frontend == null) {
      return null;
    }
    return toSecurityGroup(frontend, true);
  }

  @Override
  public HaProxySecurityGroup getById(String account, String region, String id, String vpcId) {
    // Security group ids are the frontend names.
    return get(account, region, id, vpcId);
  }

  private Collection<HaProxySecurityGroup> fromFrontends(
      Collection<CacheData> frontends, boolean includeRules) {
    return frontends.stream()
        .map(frontend -> toSecurityGroup(frontend, includeRules))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private HaProxySecurityGroup toSecurityGroup(CacheData frontend, boolean includeRules) {
    String name = HaProxyCacheKeys.getName(frontend.getId());
    String account = HaProxyCacheKeys.getAccount(frontend.getId());
    String region = HaProxyCacheKeys.getRegion(frontend.getId());
    if (name == null || account == null || region == null) {
      return null;
    }

    Map<String, Object> attributes = frontend.getAttributes();
    Set<Rule> inboundRules =
        deriveInboundRules(
            (Collection<Map<String, Object>>) attributes.get("acl_list"),
            (Collection<Map<String, Object>>) attributes.get("http_request_rule_list"),
            (Map<String, Map<String, Object>>) attributes.get("binds"));
    if (inboundRules.isEmpty()) {
      return null;
    }

    Map<String, Object> metadata = (Map<String, Object>) attributes.get("metadata");
    return HaProxySecurityGroup.builder()
        .id(name)
        .name(name)
        .accountName(account)
        .region(region)
        .moniker(namer.deriveMoniker(new NamedSection(name, metadata)))
        .inboundRules(includeRules ? inboundRules : Set.of())
        .outboundRules(Set.of())
        .build();
  }

  private Set<Rule> deriveInboundRules(
      Collection<Map<String, Object>> acls,
      Collection<Map<String, Object>> httpRequestRules,
      Map<String, Map<String, Object>> binds) {
    if (acls == null || acls.isEmpty() || httpRequestRules == null) {
      return Set.of();
    }

    // src ACLs by name: "acl office src 10.0.0.0/8 192.168.1.5"
    Map<String, String> srcAclValues = new LinkedHashMap<>();
    for (Map<String, Object> acl : acls) {
      if ("src".equals(acl.get("criterion"))
          && acl.get("acl_name") instanceof String aclName
          && acl.get("value") instanceof String value) {
        srcAclValues.put(aclName, value);
      }
    }
    if (srcAclValues.isEmpty()) {
      return Set.of();
    }

    SortedSet<Rule.PortRange> portRanges = bindPortRanges(binds);

    Set<Rule> rules = new LinkedHashSet<>();
    for (Map<String, Object> rule : httpRequestRules) {
      Object type = rule.get("type");
      if (!"allow".equals(type) && !"deny".equals(type)) {
        continue;
      }
      String condTest = rule.get("cond_test") instanceof String test ? test : "";
      String description =
          String.format("http-request %s %s %s", type, rule.get("cond"), condTest).trim();
      for (String token : condTest.split("\\s+")) {
        String aclName = token.startsWith("!") ? token.substring(1) : token;
        String value = srcAclValues.get(aclName);
        if (value == null) {
          continue;
        }
        for (String address : value.trim().split("\\s+")) {
          rules.add(
              new IpRangeRule(toRange(address), "tcp", new TreeSet<>(portRanges), description));
        }
      }
    }
    return rules;
  }

  private static SortedSet<Rule.PortRange> bindPortRanges(Map<String, Map<String, Object>> binds) {
    SortedSet<Rule.PortRange> portRanges = new TreeSet<>();
    if (binds != null) {
      for (Map<String, Object> bind : binds.values()) {
        if (bind.get("port") instanceof Number port) {
          Rule.PortRange range = new Rule.PortRange();
          range.setStartPort(port.intValue());
          range.setEndPort(port.intValue());
          portRanges.add(range);
        }
      }
    }
    return portRanges;
  }

  private static AddressableRange toRange(String address) {
    int slash = address.indexOf('/');
    if (slash >= 0) {
      return new AddressableRange(address.substring(0, slash), address.substring(slash));
    }
    return new AddressableRange(address, "/32");
  }

  /** Wraps a cached section name and metadata for moniker derivation. */
  private record NamedSection(String name, Map<String, Object> metadata)
      implements HaProxyResource {
    @Override
    public String getName() {
      return name;
    }

    @Override
    public Map<String, Object> getMetadata() {
      return metadata;
    }
  }
}
