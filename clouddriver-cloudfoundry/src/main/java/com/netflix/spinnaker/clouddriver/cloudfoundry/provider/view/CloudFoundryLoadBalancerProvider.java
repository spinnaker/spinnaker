/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository.Detail.FULL;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository.Detail.NAMES_ONLY;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys.Namespace.LOAD_BALANCERS;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import java.util.*;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
class CloudFoundryLoadBalancerProvider implements LoadBalancerProvider<CloudFoundryLoadBalancer> {
  private final Cache cacheView;
  private final CacheRepository repository;

  @Override
  public String getCloudProvider() {
    return CloudFoundryCloudProvider.ID;
  }

  @Override
  public List<CloudFoundryLoadBalancerSummary> list() {
    return new ArrayList<>(
        summarizeLoadBalancers(
                cacheView.filterIdentifiers(LOAD_BALANCERS.getNs(), Keys.getAllLoadBalancers()))
            .values());
  }

  @Nullable
  @Override
  public CloudFoundryLoadBalancerSummary get(String name) {
    return null; // intentionally null, unused
  }

  @Nullable
  @Override
  public List<CloudFoundryLoadBalancerDetail> byAccountAndRegionAndName(
      String account, String region, String name) {
    return null; // intentionally null, unused
  }

  /**
   * @return The set of CF routes that are mapped to CF apps representing server groups inside of
   *     this application. Once a route is unmapped from the app, it will no longer show up as a
   *     load balancer for the app.
   */
  @Override
  public Set<CloudFoundryLoadBalancer> getApplicationLoadBalancers(String application) {
    return repository
        .findLoadBalancersByKeys(
            cacheView.filterIdentifiers(LOAD_BALANCERS.getNs(), Keys.getAllLoadBalancers()),
            NAMES_ONLY)
        .stream()
        .filter(
            lb ->
                lb.getServerGroups().stream()
                    .anyMatch(
                        serverGroup ->
                            application.equals(Names.parseName(serverGroup.getName()).getApp())))
        .collect(toSet());
  }

  private Map<String, CloudFoundryLoadBalancerSummary> summarizeLoadBalancers(
      Collection<String> loadBalancerKeys) {
    Map<String, CloudFoundryLoadBalancerSummary> summariesByAccount = new HashMap<>();

    for (CloudFoundryLoadBalancer loadBalancer :
        repository.findLoadBalancersByKeys(loadBalancerKeys, FULL)) {
      String account = loadBalancer.getAccount();
      CloudFoundryLoadBalancerSummary summary =
          summariesByAccount.computeIfAbsent(account, CloudFoundryLoadBalancerSummary::new);

      CloudFoundryLoadBalancerDetail detail =
          new CloudFoundryLoadBalancerDetail(
              account, loadBalancer.getName(), loadBalancer.getSpace());

      summary
          .accounts
          .computeIfAbsent(account, CloudFoundryLoadBalancerAccount::new)
          .regions
          .computeIfAbsent(
              loadBalancer.getSpace().getRegion(), CloudFoundryLoadBalancerAccountRegion::new)
          .loadBalancers
          .add(detail);
    }

    return summariesByAccount;
  }

  @RequiredArgsConstructor
  @Getter
  public static class CloudFoundryLoadBalancerSummary implements Item {
    private final String name;

    @JsonIgnore
    private final Map<String, CloudFoundryLoadBalancerAccount> accounts = new HashMap<>();

    @Override
    public List<CloudFoundryLoadBalancerAccount> getByAccounts() {
      return new ArrayList<>(accounts.values());
    }
  }

  @RequiredArgsConstructor
  @Getter
  public static class CloudFoundryLoadBalancerAccount implements ByAccount {
    private final String name;

    @JsonIgnore
    private final Map<String, CloudFoundryLoadBalancerAccountRegion> regions = new HashMap<>();

    @JsonProperty("regions")
    public List<CloudFoundryLoadBalancerAccountRegion> getByRegions() {
      return new ArrayList<>(regions.values());
    }
  }

  @RequiredArgsConstructor
  @Getter
  public static class CloudFoundryLoadBalancerAccountRegion implements ByRegion {
    private final String name;
    private final List<CloudFoundryLoadBalancerDetail> loadBalancers = new ArrayList<>();
  }

  @RequiredArgsConstructor
  @Getter
  public static class CloudFoundryLoadBalancerDetail implements Details {
    private final String account;
    private final String name;
    private final CloudFoundrySpace space;

    public String getType() {
      return "cf";
    }
  }
}
