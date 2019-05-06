/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.servicediscovery.AWSServiceDiscovery;
import com.amazonaws.services.servicediscovery.model.ListServicesRequest;
import com.amazonaws.services.servicediscovery.model.ListServicesResult;
import com.amazonaws.services.servicediscovery.model.ServiceSummary;
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
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICE_DISCOVERY_REGISTRIES;

public class ServiceDiscoveryCachingAgent implements CachingAgent {
  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(SERVICE_DISCOVERY_REGISTRIES.toString())
  ));

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ObjectMapper objectMapper;
  private AmazonClientProvider amazonClientProvider;
  private AWSCredentialsProvider awsCredentialsProvider;
  private NetflixAmazonCredentials account;
  private String accountName;
  private String region;

  public ServiceDiscoveryCachingAgent(NetflixAmazonCredentials account, String region,
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

  public static Map<String, Object> convertServiceToAttributes(String accountName, String region, ServiceSummary service) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("account", accountName);
    attributes.put("region", region);
    attributes.put("serviceName", service.getName());
    attributes.put("serviceArn", service.getArn());
    attributes.put("serviceId", service.getId());
    return attributes;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    AWSServiceDiscovery serviceDiscoveryClient = amazonClientProvider.getAmazonServiceDiscovery(account, region, false);

    Set<ServiceSummary> services = fetchServices(serviceDiscoveryClient);
    Map<String, Collection<CacheData>> newDataMap = generateFreshData(services);
    Collection<CacheData> newData = newDataMap.get(SERVICE_DISCOVERY_REGISTRIES.toString());

    Set<String> oldKeys = providerCache.getAll(SERVICE_DISCOVERY_REGISTRIES.toString()).stream()
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
    evictionsByKey.put(SERVICE_DISCOVERY_REGISTRIES.toString(), evictedKeys);
    log.info("Evicting " + evictedKeys.size() + " service discovery services in " + getAgentType());
    return evictionsByKey;
  }

  Map<String, Collection<CacheData>> generateFreshData(Set<ServiceSummary> services) {
    Collection<CacheData> dataPoints = new HashSet<>();
    Map<String, Collection<CacheData>> newDataMap = new HashMap<>();

    for (ServiceSummary service : services) {
      String key = Keys.getServiceDiscoveryRegistryKey(accountName, region, service.getId());
      Map<String, Object> attributes = convertServiceToAttributes(accountName, region, service);

      CacheData data = new DefaultCacheData(key, attributes, Collections.emptyMap());
      dataPoints.add(data);
    }

    log.info("Caching " + dataPoints.size() + " service discovery services in " + getAgentType());
    newDataMap.put(SERVICE_DISCOVERY_REGISTRIES.toString(), dataPoints);
    return newDataMap;
  }

  Set<ServiceSummary> fetchServices(AWSServiceDiscovery serviceDiscoveryClient) {
    Set<ServiceSummary> services = new HashSet<>();
    String nextToken = null;
    do {
      ListServicesRequest request = new ListServicesRequest();
      if (nextToken != null) {
        request.setNextToken(nextToken);
      }

      ListServicesResult result = serviceDiscoveryClient.listServices(request);
      services.addAll(result.getServices());

      nextToken = result.getNextToken();
    } while (nextToken != null && nextToken.length() != 0);

    return services;
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
