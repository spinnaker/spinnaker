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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.HttpCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.security.AbstractAccountCredentials;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@JsonIgnoreProperties({
  "credentials",
  "client",
  "password",
  "spaceSupplier",
  "cacheRepository",
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

  private final int maxCapiConnectionsForCache;

  private final Supplier<List<CloudFoundrySpace>> spaceSupplier =
      Memoizer.memoizeWithExpiration(this::spaceSupplier, SPACE_EXPIRY_SECONDS, TimeUnit.SECONDS);

  private CloudFoundryClient credentials;

  private CacheRepository cacheRepository;

  private Permissions permissions;

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
      Integer maxCapiConnectionsForCache,
      CacheRepository cacheRepository,
      Permissions permissions) {
    this.name = name;
    this.appsManagerUri = appsManagerUri;
    this.metricsUri = metricsUri;
    this.apiHost = apiHost;
    this.userName = userName;
    this.password = password;
    this.environment = Optional.ofNullable(environment).orElse("dev");
    this.skipSslValidation = skipSslValidation;
    this.resultsPerPage = Optional.ofNullable(resultsPerPage).orElse(100);
    this.maxCapiConnectionsForCache = Optional.ofNullable(maxCapiConnectionsForCache).orElse(16);
    this.cacheRepository = cacheRepository;
    this.permissions = permissions == null ? Permissions.EMPTY : permissions;
  }

  public CloudFoundryClient getCredentials() {
    if (this.credentials == null) {
      this.credentials =
          new HttpCloudFoundryClient(
              name,
              appsManagerUri,
              metricsUri,
              apiHost,
              userName,
              password,
              skipSslValidation,
              resultsPerPage,
              maxCapiConnectionsForCache);
    }
    return credentials;
  }

  public CloudFoundryClient getClient() {
    return getCredentials();
  }

  public Collection<Map<String, String>> getRegions() {
    return spaceSupplier.get().stream()
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

  public List<CloudFoundrySpace> getSpacesLive() {
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

    public T get() {
      return cache.get(CACHE_KEY);
    }

    public static <U> Memoizer<U> memoizeWithExpiration(
        Supplier<U> supplier, long expirySeconds, TimeUnit timeUnit) {
      return new Memoizer<>(supplier, expirySeconds, timeUnit);
    }
  }
}
