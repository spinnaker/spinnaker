/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.security;

import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.HttpCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.security.AbstractAccountCredentials;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
@Getter
@JsonIgnoreProperties({
  "credentials",
  "client",
  "password",
  "spaceSupplier",
  "cacheRepository",
  "forkJoinPool",
  "filteredSpaces",
  "spacesLive"
})
public class CloudFoundryCredentials extends AbstractAccountCredentials<CloudFoundryClient> {
  private static final int SPACE_EXPIRY_SECONDS = 30;

  private final String name;
  private final String appsManagerUri;
  private final String metricsUri;
  private final String apiHost;
  private final String userName;
  private final String password;

  @Nullable private final String environment;

  private final String accountType = "cloudfoundry";

  private final String cloudProvider = "cloudfoundry";

  @Deprecated private final List<String> requiredGroupMembership = Collections.emptyList();
  private final boolean skipSslValidation;

  @Nullable private final Integer resultsPerPage;

  private final Supplier<List<CloudFoundrySpace>> spaceSupplier =
      Memoizer.memoizeWithExpiration(this::spaceSupplier, SPACE_EXPIRY_SECONDS, TimeUnit.SECONDS);

  private CacheRepository cacheRepository;

  private Permissions permissions;

  private final ForkJoinPool forkJoinPool;

  private final List<CloudFoundrySpace> filteredSpaces;

  private final CloudFoundryClient cloudFoundryClient;

  public CloudFoundryCredentials(
      String name,
      String appsManagerUri,
      String metricsUri,
      String apiHost,
      String userName,
      String password,
      String environment,
      boolean skipSslValidation,
      Integer resultsPerPage,
      CacheRepository cacheRepository,
      Permissions permissions,
      ForkJoinPool forkJoinPool,
      Map<String, Set<String>> spaceFilter,
      OkHttpClient okHttpClient,
      CloudFoundryConfigurationProperties.ClientConfig clientConfig) {
    this.name = name;
    this.appsManagerUri = appsManagerUri;
    this.metricsUri = metricsUri;
    this.apiHost = apiHost;
    this.userName = userName;
    this.password = password;
    this.environment = Optional.ofNullable(environment).orElse("dev");
    this.skipSslValidation = skipSslValidation;
    this.resultsPerPage = Optional.ofNullable(resultsPerPage).orElse(100);
    this.cacheRepository = cacheRepository;
    this.permissions = permissions == null ? Permissions.EMPTY : permissions;
    this.forkJoinPool = forkJoinPool;
    this.cloudFoundryClient =
        new HttpCloudFoundryClient(
            name,
            appsManagerUri,
            metricsUri,
            apiHost,
            userName,
            password,
            true,
            skipSslValidation,
            resultsPerPage,
            forkJoinPool,
            okHttpClient.newBuilder(),
            clientConfig);
    this.filteredSpaces = createFilteredSpaces(spaceFilter);
  }

  public CloudFoundryClient getCredentials() {
    return getClient();
  }

  public CloudFoundryClient getClient() {
    return cloudFoundryClient;
  }

  public Collection<Map<String, String>> getRegions() {
    return spaceSupplier.get().stream()
        .filter(
            s -> {
              if (!filteredSpaces.isEmpty()) {
                List<String> filteredRegions =
                    filteredSpaces.stream().map(fs -> fs.getRegion()).collect(toList());
                return filteredRegions.contains(s.getRegion());
              }
              return true;
            })
        .map(space -> singletonMap("name", space.getRegion()))
        .collect(toList());
  }

  protected List<CloudFoundrySpace> spaceSupplier() {
    Set<CloudFoundrySpace> spaces = cacheRepository.findSpacesByAccount(name);
    if (!spaces.isEmpty()) {
      return new ArrayList<>(spaces);
    }
    return getSpacesLive();
  }

  private List<CloudFoundrySpace> getSpacesLive() {
    try {
      return getClient().getSpaces().all();
    } catch (CloudFoundryApiException e) {
      log.warn("Unable to determine regions for Cloud Foundry account " + name, e);
      return emptyList();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CloudFoundryCredentials)) {
      return false;
    }
    CloudFoundryCredentials that = (CloudFoundryCredentials) o;
    return name.equals(that.name)
        && Objects.equals(appsManagerUri, that.appsManagerUri)
        && Objects.equals(metricsUri, that.metricsUri)
        && Objects.equals(userName, that.userName)
        && Objects.equals(password, that.password)
        && Objects.equals(environment, that.environment)
        && Objects.equals(skipSslValidation, that.skipSslValidation)
        && Objects.equals(resultsPerPage, that.resultsPerPage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name,
        appsManagerUri,
        metricsUri,
        userName,
        password,
        environment,
        skipSslValidation,
        resultsPerPage);
  }

  protected List<CloudFoundrySpace> createFilteredSpaces(Map<String, Set<String>> spaceFilter) {
    List<CloudFoundrySpace> spaces = new ArrayList<>();
    if (spaceFilter.isEmpty() || spaceFilter == null) {
      return emptyList();
    }

    Set<String> filteredRegions = new HashSet<>();
    // IF an Org is provided without spaces -> add all spaces for the ORG
    for (String orgName : spaceFilter.keySet()) {
      if (spaceFilter.get(orgName).isEmpty() || spaceFilter.get(orgName) == null) {
        List<CloudFoundrySpace> allSpacesByOrg =
            this.getClient()
                .getSpaces()
                .findAllBySpaceNamesAndOrgNames(null, singletonList(orgName));
        spaces.addAll(allSpacesByOrg);
      } else {
        for (String spaceName : spaceFilter.get(orgName)) {
          filteredRegions.add(orgName + " > " + spaceName);
        }
      }
    }
    // IF an Org is provided with spaces -> add all spaces that are in the ORG and filteredRegions
    List<CloudFoundrySpace> allSpaces =
        this.getClient()
            .getSpaces()
            .findAllBySpaceNamesAndOrgNames(
                spaceFilter.values().stream().flatMap(l -> l.stream()).collect(Collectors.toList()),
                List.copyOf(spaceFilter.keySet()));
    allSpaces.stream()
        .filter(s -> filteredRegions.contains(s.getRegion()))
        .forEach(s -> spaces.add(s));

    if (spaces.isEmpty())
      throw new IllegalArgumentException(
          "The spaceFilter had Orgs and/or Spaces but CloudFoundry returned no spaces as a result. Spaces must not be null or empty when a spaceFilter is included.");

    return ImmutableList.copyOf(spaces);
  }

  /**
   * Thin wrapper around a Caffeine cache that handles memoizing a supplier function with expiration
   */
  private static class Memoizer<T> implements Supplier<T> {
    private static final String CACHE_KEY = "key";
    private final LoadingCache<String, T> cache;

    private Memoizer(Supplier<T> supplier, long expirySeconds, TimeUnit timeUnit) {
      this.cache =
          Caffeine.newBuilder()
              .refreshAfterWrite(expirySeconds, timeUnit)
              .build(key -> supplier.get());
    }

    public static <U> Memoizer<U> memoizeWithExpiration(
        Supplier<U> supplier, long expirySeconds, TimeUnit timeUnit) {
      return new Memoizer<>(supplier, expirySeconds, timeUnit);
    }

    public T get() {
      return cache.get(CACHE_KEY);
    }
  }
}
