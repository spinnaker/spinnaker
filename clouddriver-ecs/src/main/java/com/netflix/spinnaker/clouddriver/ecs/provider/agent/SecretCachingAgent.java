/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.ListSecretsRequest;
import com.amazonaws.services.secretsmanager.model.ListSecretsResult;
import com.amazonaws.services.secretsmanager.model.SecretListEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SECRETS;

public class SecretCachingAgent implements CachingAgent {
  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(SECRETS.toString())
  ));

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ObjectMapper objectMapper;
  private AmazonClientProvider amazonClientProvider;
  private AWSCredentialsProvider awsCredentialsProvider;
  private NetflixAmazonCredentials account;
  private String accountName;
  private String region;

  public SecretCachingAgent(NetflixAmazonCredentials account, String region,
                            AmazonClientProvider amazonClientProvider,
                            AWSCredentialsProvider awsCredentialsProvider,
                            ObjectMapper objectMapper) {
    this.region = region;
    this.account = account;
    this.accountName = account.getName();
    this.amazonClientProvider = amazonClientProvider;
    this.awsCredentialsProvider = awsCredentialsProvider;
    this.objectMapper = objectMapper;
  }

  public static Map<String, Object> convertSecretToAttributes(String accountName, String region, SecretListEntry secret) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("account", accountName);
    attributes.put("region", region);
    attributes.put("secretName", secret.getName());
    attributes.put("secretArn", secret.getARN());
    return attributes;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    AWSSecretsManager secretsManagerClient = amazonClientProvider.getAmazonSecretsManager(account, region, false);

    Set<SecretListEntry> secrets = fetchSecrets(secretsManagerClient);
    Map<String, Collection<CacheData>> newDataMap = generateFreshData(secrets);
    Collection<CacheData> newData = newDataMap.get(SECRETS.toString());

    Set<String> oldKeys = providerCache.getAll(SECRETS.toString()).stream()
      .map(CacheData::getId)
      .filter(this::keyAccountRegionFilter)
      .collect(Collectors.toSet());

    Map<String, Collection<String>> evictionsByKey = computeEvictableData(newData, oldKeys);

    return new DefaultCacheResult(newDataMap, evictionsByKey);
  }

  private Map<String, Collection<String>> computeEvictableData(Collection<CacheData> newData, Collection<String> oldKeys) {
    Set<String> newKeys = newData.stream().map(CacheData::getId).collect(Collectors.toSet());
    Set<String> evictedKeys = oldKeys.stream().filter(oldKey -> !newKeys.contains(oldKey)).collect(Collectors.toSet());

    Map<String, Collection<String>> evictionsByKey = new HashMap<>();
    evictionsByKey.put(SECRETS.toString(), evictedKeys);
    log.info("Evicting " + evictedKeys.size() + " secrets in " + getAgentType());
    return evictionsByKey;
  }

  Map<String, Collection<CacheData>> generateFreshData(Set<SecretListEntry> secrets) {
    Collection<CacheData> dataPoints = new HashSet<>();
    Map<String, Collection<CacheData>> newDataMap = new HashMap<>();

    for (SecretListEntry secret : secrets) {
      String key = Keys.getSecretKey(accountName, region, secret.getName());
      Map<String, Object> attributes = convertSecretToAttributes(accountName, region, secret);

      CacheData data = new DefaultCacheData(key, attributes, Collections.emptyMap());
      dataPoints.add(data);
    }

    log.info("Caching " + dataPoints.size() + " secrets in " + getAgentType());
    newDataMap.put(SECRETS.toString(), dataPoints);
    return newDataMap;
  }

  Set<SecretListEntry> fetchSecrets(AWSSecretsManager secretsManagerClient) {
    Set<SecretListEntry> secrets = new HashSet<>();
    String nextToken = null;
    do {
      ListSecretsRequest request = new ListSecretsRequest();
      if (nextToken != null) {
        request.setNextToken(nextToken);
      }

      ListSecretsResult result = secretsManagerClient.listSecrets(request);
      secrets.addAll(result.getSecretList());

      nextToken = result.getNextToken();
    } while (nextToken != null && nextToken.length() != 0);

    return secrets;
  }

  private boolean keyAccountRegionFilter(String key) {
    Map<String, String> keyParts = Keys.parse(key);
    return keyParts != null &&
      keyParts.get("account").equals(accountName) &&
      keyParts.get("region").equals(region);
  }

  @Override
  public String getAgentType() {
    return accountName + "/" + region + "/" + getClass().getSimpleName();
  }

  @Override
  public String getProviderName() {
    return EcsProvider.NAME;
  }
}
