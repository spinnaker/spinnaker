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

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.IAM_ROLE;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.google.common.base.CaseFormat;
import com.netflix.spinnaker.cats.agent.AgentDataType;
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

abstract class AbstractEcsCachingAgent<T> implements CachingAgent {
  private final Logger log = LoggerFactory.getLogger(getClass());

  final AmazonClientProvider amazonClientProvider;
  final AWSCredentialsProvider awsCredentialsProvider;
  final NetflixAmazonCredentials account;
  final String region;
  final String accountName;

  AbstractEcsCachingAgent(
      NetflixAmazonCredentials account,
      String region,
      AmazonClientProvider amazonClientProvider,
      AWSCredentialsProvider awsCredentialsProvider) {
    this.account = account;
    this.accountName = account.getName();
    this.region = region;
    this.amazonClientProvider = amazonClientProvider;
    this.awsCredentialsProvider = awsCredentialsProvider;
  }

  /**
   * Fetches items from the ECS service.
   *
   * @param ecs The AmazonECS client that will be used to make the queries.
   * @param providerCache A ProviderCache that is used to access already existing cache.
   * @return A list of generic type objects.
   */
  protected abstract List<T> getItems(AmazonECS ecs, ProviderCache providerCache);

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
    String authoritativeKeyName = getAuthoritativeKeyName();

    AmazonECS ecs = amazonClientProvider.getAmazonEcs(account, region, false);
    List<T> items = getItems(ecs, providerCache);
    return buildCacheResult(authoritativeKeyName, items, providerCache);
  }

  /**
   * Provides a set of ECS cluster ARNs. Either uses the cache, or queries the ECS service.
   *
   * @param ecs The AmazonECS client to use for querying.
   * @param providerCache The ProviderCache to retrieve clusters from.
   * @return A set of ECS cluster ARNs.
   */
  Set<String> getClusters(AmazonECS ecs, ProviderCache providerCache) {
    Set<String> clusters =
        providerCache.getAll(ECS_CLUSTERS.toString()).stream()
            .filter(
                cacheData ->
                    cacheData.getAttributes().get("region").equals(region)
                        && cacheData.getAttributes().get("account").equals(accountName))
            .map(cacheData -> (String) cacheData.getAttributes().get("clusterArn"))
            .collect(Collectors.toSet());

    if (clusters == null || clusters.isEmpty()) {
      clusters = new HashSet<>();
      String nextToken = null;
      do {
        ListClustersRequest listClustersRequest = new ListClustersRequest();
        if (nextToken != null) {
          listClustersRequest.setNextToken(nextToken);
        }
        ListClustersResult listClustersResult = ecs.listClusters(listClustersRequest);
        clusters.addAll(listClustersResult.getClusterArns());

        nextToken = listClustersResult.getNextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }

    return clusters;
  }

  /**
   * Provides the key namespace that the caching agent is authoritative of. Currently only supports
   * the caching agent being authoritative over one key namespace.
   *
   * @return Key namespace.
   */
  String getAuthoritativeKeyName() {
    Collection<AgentDataType> authoritativeNamespaces =
        getProvidedDataTypes().stream()
            .filter(agentDataType -> agentDataType.getAuthority().equals(AUTHORITATIVE))
            .collect(Collectors.toSet());

    if (authoritativeNamespaces.size() != 1) {
      throw new RuntimeException(
          "AbstractEcsCachingAgent supports only one authoritative key namespace. "
              + authoritativeNamespaces.size()
              + " authoritative key namespace were given.");
    }

    return authoritativeNamespaces.iterator().next().getTypeName();
  }

  CacheResult buildCacheResult(
      String authoritativeKeyName, List<T> items, ProviderCache providerCache) {
    String prettyKeyName =
        CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, authoritativeKeyName);

    Map<String, Collection<CacheData>> dataMap = generateFreshData(items);

    // Old keys can come from different account/region, filter them to the current account/region.
    Set<String> oldKeys =
        providerCache.getAll(authoritativeKeyName).stream()
            .map(CacheData::getId)
            .filter(key -> keyAccountRegionFilter(authoritativeKeyName, key))
            .collect(Collectors.toSet());

    Map<String, Collection<String>> evictions =
        computeEvictableData(dataMap.get(authoritativeKeyName), oldKeys);
    evictions = addExtraEvictions(evictions);
    log.info(
        "Evicting "
            + evictions.size()
            + " "
            + prettyKeyName
            + (evictions.size() > 1 ? "s" : "")
            + " in "
            + getAgentType());

    return new DefaultCacheResult(dataMap, evictions);
  }

  /**
   * Evicts cache that does not belong to an entity on the ECS service. This is done by evicting old
   * keys that are no longer found in the new keys provided by the new data.
   *
   * @param newData New data that contains new keys.
   * @param oldKeys Old keys.
   * @return Key collection associated to the key namespace the the caching agent is authoritative
   *     of.
   */
  private Map<String, Collection<String>> computeEvictableData(
      Collection<CacheData> newData, Collection<String> oldKeys) {
    // New data can only come from the current account and region, no need to filter.
    Set<String> newKeys = newData.stream().map(CacheData::getId).collect(Collectors.toSet());

    Set<String> evictedKeys =
        oldKeys.stream().filter(oldKey -> !newKeys.contains(oldKey)).collect(Collectors.toSet());

    Map<String, Collection<String>> evictionsByKey = new HashMap<>();
    evictionsByKey.put(getAuthoritativeKeyName(), evictedKeys);

    return evictionsByKey;
  }

  protected boolean keyAccountRegionFilter(String authoritativeKeyName, String key) {
    Map<String, String> keyParts = Keys.parse(key);
    return keyParts != null
        && keyParts.get("account").equals(accountName)
        &&
        // IAM role keys are not region specific, so it will be true. The region will be checked of
        // other keys.
        (authoritativeKeyName.equals(IAM_ROLE.ns) || keyParts.get("region").equals(region));
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
}
