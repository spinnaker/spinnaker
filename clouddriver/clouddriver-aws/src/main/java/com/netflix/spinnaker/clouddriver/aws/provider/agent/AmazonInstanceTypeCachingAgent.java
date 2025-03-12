/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstanceTypesRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceTypesResult;
import com.amazonaws.services.ec2.model.GpuInfo;
import com.amazonaws.services.ec2.model.InstanceStorageInfo;
import com.amazonaws.services.ec2.model.InstanceTypeInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.cache.Keys;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AmazonInstanceTypeCachingAgent implements CachingAgent, AccountAware {

  private static final TypeReference<Map<String, Object>> ATTRIBUTES =
      new TypeReference<Map<String, Object>>() {};

  private final String region;
  private final AmazonClientProvider amazonClientProvider;
  private final NetflixAmazonCredentials account;
  private final ObjectMapper objectMapper;

  public AmazonInstanceTypeCachingAgent(
      String region,
      AmazonClientProvider amazonClientProvider,
      NetflixAmazonCredentials account,
      ObjectMapper objectMapper) {
    this.account = account;
    this.amazonClientProvider = amazonClientProvider;
    this.region = region;
    this.objectMapper = objectMapper;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return Collections.unmodifiableList(
        Arrays.asList(
            AUTHORITATIVE.forType(Keys.Namespace.INSTANCE_TYPES.getNs()),
            AUTHORITATIVE.forType(getAgentType())));
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    AmazonEC2 amazonEC2 = amazonClientProvider.getAmazonEC2(this.account, this.region);
    final List<InstanceTypeInfo> instanceTypesInfo = getInstanceTypes(amazonEC2);

    Map<String, Collection<CacheData>> cacheResults = new HashMap<>();

    // cache instance types for key "metadata" for backwards compatibility
    Set<String> instanceTypes =
        instanceTypesInfo.stream()
            .map(InstanceTypeInfo::getInstanceType)
            .collect(Collectors.toSet());
    DefaultCacheData metadata = buildCacheDataForMetadataKey(providerCache, instanceTypes);
    cacheResults.put(getAgentType(), Collections.singleton(metadata));

    // cache instance types info
    if (instanceTypesInfo == null || instanceTypesInfo.isEmpty()) {
      return new DefaultCacheResult(cacheResults);
    }

    List<CacheData> instanceTypeData =
        instanceTypesInfo.stream()
            .map(
                i -> {
                  Map<String, Object> attributes = objectMapper.convertValue(i, ATTRIBUTES);
                  attributes.put("account", account.getName());
                  attributes.put("region", region);
                  attributes.put("name", i.getInstanceType());
                  attributes.put("defaultVCpus", i.getVCpuInfo().getDefaultVCpus());
                  attributes.put("memoryInGiB", i.getMemoryInfo().getSizeInMiB() / 1024);
                  attributes.put(
                      "supportedArchitectures", i.getProcessorInfo().getSupportedArchitectures());

                  if (i.getInstanceStorageInfo() != null) {
                    InstanceStorageInfo info = i.getInstanceStorageInfo();
                    Map<String, Object> instanceStorageAttributes = new HashMap<>();

                    instanceStorageAttributes.put("totalSizeInGB", info.getTotalSizeInGB());
                    if (info.getDisks() != null && info.getDisks().size() > 0) {
                      instanceStorageAttributes.put(
                          "storageTypes",
                          info.getDisks().stream()
                              .map(d -> d.getType())
                              .collect(Collectors.joining(",")));
                    }
                    if (info.getNvmeSupport() != null) {
                      instanceStorageAttributes.put("nvmeSupport", info.getNvmeSupport());
                    }
                    attributes.put("instanceStorageInfo", instanceStorageAttributes);
                  }

                  if (i.getGpuInfo() != null) {
                    GpuInfo info = i.getGpuInfo();
                    Map<String, Object> gpuInfoAttributes = new HashMap<>();

                    if (info.getTotalGpuMemoryInMiB() != null) {
                      gpuInfoAttributes.put("totalGpuMemoryInMiB", info.getTotalGpuMemoryInMiB());
                    }
                    if (info.getGpus() != null) {
                      gpuInfoAttributes.put(
                          "gpus",
                          info.getGpus().stream()
                              .map(
                                  g -> {
                                    Map<String, Object> gpuDeviceInfo = new HashMap<>();
                                    gpuDeviceInfo.put("name", g.getName());
                                    gpuDeviceInfo.put("manufacturer", g.getManufacturer());
                                    gpuDeviceInfo.put("count", g.getCount());
                                    gpuDeviceInfo.put(
                                        "gpuSizeInMiB", g.getMemoryInfo().getSizeInMiB());
                                    return gpuDeviceInfo;
                                  })
                              .collect(Collectors.toList()));
                    }
                    attributes.put("gpuInfo", gpuInfoAttributes);
                  }

                  if (i.getNetworkInfo() != null) {
                    attributes.put("ipv6Supported", i.getNetworkInfo().getIpv6Supported());
                  }

                  return new DefaultCacheData(
                      Keys.getInstanceTypeKey(i.getInstanceType(), region, account.getName()),
                      attributes,
                      Collections.emptyMap());
                })
            .collect(Collectors.toList());
    cacheResults.put(Keys.Namespace.INSTANCE_TYPES.getNs(), instanceTypeData);

    return new DefaultCacheResult(cacheResults);
  }

  private DefaultCacheData buildCacheDataForMetadataKey(
      ProviderCache providerCache, final Set<String> instanceTypes) {
    CacheData metadata =
        providerCache.get(getAgentType(), "metadata", RelationshipCacheFilter.none());
    MetadataAttributes metadataAttributes;

    if (metadata != null) {
      metadataAttributes =
          objectMapper.convertValue(metadata.getAttributes(), MetadataAttributes.class);
    } else {
      MetadataAttributes newMetadataAttributes = new MetadataAttributes();
      newMetadataAttributes.cachedInstanceTypes = instanceTypes;
      metadataAttributes = newMetadataAttributes;
    }

    return new DefaultCacheData(
        "metadata",
        objectMapper.convertValue(metadataAttributes, ATTRIBUTES),
        Collections.emptyMap());
  }

  private List<InstanceTypeInfo> getInstanceTypes(AmazonEC2 ec2) {
    final List<InstanceTypeInfo> instanceTypeInfoList = new ArrayList<>();
    final DescribeInstanceTypesRequest request = new DescribeInstanceTypesRequest();
    while (true) {
      final DescribeInstanceTypesResult result = ec2.describeInstanceTypes(request);
      instanceTypeInfoList.addAll(result.getInstanceTypes());
      if (result.getNextToken() != null) {
        request.withNextToken(result.getNextToken());
      } else {
        break;
      }
    }

    return instanceTypeInfoList;
  }

  @Override
  public String getAgentType() {
    return getClass().getSimpleName() + "/" + region;
  }

  @Override
  public String getProviderName() {
    return AwsInfrastructureProvider.PROVIDER_NAME;
  }

  @Override
  public String getAccountName() {
    return account.getName();
  }

  static class MetadataAttributes {
    public Set<String> cachedInstanceTypes;
  }
}
