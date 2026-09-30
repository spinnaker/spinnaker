/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.IAM_ROLE;

import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.ListClustersRequest;
import software.amazon.awssdk.services.ecs.model.ListClustersResponse;

abstract class AbstractEcsCachingAgent<T> implements CachingAgent, AccountAware {
  private final Logger log = LoggerFactory.getLogger(getClass());

  final AmazonClientProvider amazonClientProvider;
  final NetflixAmazonCredentials account;
  final String region;
  final String accountName;

  AbstractEcsCachingAgent(
      NetflixAmazonCredentials account, String region, AmazonClientProvider amazonClientProvider) {
    this.account = account;
    this.accountName = account.getName();
    this.region = region;
    this.amazonClientProvider = amazonClientProvider;
  }

  /**
   * Fetches items from the ECS service.
   *
   * @param ecs The EcsClient that will be used to make the queries.
   * @param providerCache A ProviderCache that is used to access already existing cache.
   * @return A list of generic type objects.
   */
  protected abstract List<T> getItems(EcsClient ecs, ProviderCache providerCache);

  /**
   * Generates a map of CacheData collections associated to a key namespace from a given collection
   * of generic type objects.
   *
   * @param cacheableItems A collection of generic type objects.
   * @return A map of CacheData collections belonging to a key namespace.
   */
  protected abstract Map<String, Collection<CacheData>> generateFreshData(
      Collection<T> cacheableItems);

  @Override
  public String getProviderName() {
    return EcsProvider.NAME;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    EcsClient ecs = amazonClientProvider.getAmazonEcsV2(account, region);
    List<T> items = getItems(ecs, providerCache);
    return buildCacheResult(items);
  }

  /**
   * Provides a set of ECS cluster ARNs. Either uses the cache, or queries the ECS service.
   *
   * @param ecs The AmazonECS client to use for querying.
   * @param providerCache The ProviderCache to retrieve clusters from.
   * @return A set of ECS cluster ARNs.
   */
  Set<String> getClusters(EcsClient ecs, ProviderCache providerCache) {
    Set<String> clusters =
        providerCache
            .getAll(
                ECS_CLUSTERS.toString(),
                providerCache.filterIdentifiers(
                    ECS_CLUSTERS.toString(), Keys.buildGlob(ECS_CLUSTERS, accountName, region)))
            .stream()
            .map(cacheData -> (String) cacheData.getAttributes().get("clusterArn"))
            .collect(Collectors.toSet());

    if (clusters == null || clusters.isEmpty()) {
      clusters = new HashSet<>();
      String nextToken = null;
      do {
        ListClustersRequest.Builder requestBuilder = ListClustersRequest.builder();
        if (nextToken != null) {
          requestBuilder.nextToken(nextToken);
        }
        ListClustersResponse listClustersResult = ecs.listClusters(requestBuilder.build());
        clusters.addAll(listClustersResult.clusterArns());

        nextToken = listClustersResult.nextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }
    return clusters;
  }

  CacheResult buildCacheResult(List<T> items) {
    Map<String, Collection<CacheData>> dataMap = generateFreshData(items);
    Map<String, Collection<String>> evictions = addExtraEvictions(new HashMap<>());

    return new DefaultCacheResult(dataMap, evictions);
  }

  protected boolean keyAccountRegionFilter(String authoritativeKeyName, String key) {
    Map<String, String> keyParts = Keys.parse(key);
    return keyParts != null
        && ((accountName.equals(keyParts.get("account"))
                &&
                // IAM role keys are not region specific, so it will be true. The region will be
                // checked of other keys.
                (authoritativeKeyName.equals(IAM_ROLE.ns) || keyParts.get("region").equals(region)))
            // Application keys are not account or region specific so this will be true. The region
            // and
            // account will be checked for other keys.
            || (authoritativeKeyName.equals(ECS_APPLICATIONS.ns)));
  }

  /**
   * This method is to be overridden in order to add extra evictions.
   *
   * @param evictions The existing eviction map.
   * @return Eviction map with addtional keys.
   */
  protected Map<String, Collection<String>> addExtraEvictions(
      Map<String, Collection<String>> evictions) {
    return evictions;
  }

  /**
   * Returns the account name with which this agent is associated.
   *
   * @return The name of the account this agent handles.
   */
  @Override
  public String getAccountName() {
    return accountName;
  }
}
