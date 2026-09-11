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
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceTypesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceTypesResponse;
import software.amazon.awssdk.services.ec2.model.GpuInfo;
import software.amazon.awssdk.services.ec2.model.InstanceStorageInfo;
import software.amazon.awssdk.services.ec2.model.InstanceTypeInfo;

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
    Ec2Client amazonEC2 = amazonClientProvider.getAmazonEC2V2(this.account, this.region);
    final List<InstanceTypeInfo> instanceTypesInfo = getInstanceTypes(amazonEC2);

    Map<String, Collection<CacheData>> cacheResults = new HashMap<>();

    // cache instance types for key "metadata" for backwards compatibility
    Set<String> instanceTypes =
        instanceTypesInfo.stream()
            .map(InstanceTypeInfo::instanceTypeAsString)
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
                  attributes.put("name", i.instanceTypeAsString());
                  attributes.put("defaultVCpus", i.vCpuInfo().defaultVCpus());
                  attributes.put("memoryInGiB", i.memoryInfo().sizeInMiB() / 1024);
                  attributes.put(
                      "supportedArchitectures", i.processorInfo().supportedArchitectures());

                  if (i.instanceStorageInfo() != null) {
                    InstanceStorageInfo info = i.instanceStorageInfo();
                    Map<String, Object> instanceStorageAttributes = new HashMap<>();

                    instanceStorageAttributes.put("totalSizeInGB", info.totalSizeInGB());
                    if (info.disks() != null && info.disks().size() > 0) {
                      instanceStorageAttributes.put(
                          "storageTypes",
                          info.disks().stream()
                              .map(d -> d.typeAsString())
                              .collect(Collectors.joining(",")));
                    }
                    if (info.nvmeSupport() != null) {
                      instanceStorageAttributes.put("nvmeSupport", info.nvmeSupportAsString());
                    }
                    attributes.put("instanceStorageInfo", instanceStorageAttributes);
                  }

                  if (i.gpuInfo() != null) {
                    GpuInfo info = i.gpuInfo();
                    Map<String, Object> gpuInfoAttributes = new HashMap<>();

                    if (info.totalGpuMemoryInMiB() != null) {
                      gpuInfoAttributes.put("totalGpuMemoryInMiB", info.totalGpuMemoryInMiB());
                    }
                    if (info.gpus() != null) {
                      gpuInfoAttributes.put(
                          "gpus",
                          info.gpus().stream()
                              .map(
                                  g -> {
                                    Map<String, Object> gpuDeviceInfo = new HashMap<>();
                                    gpuDeviceInfo.put("name", g.name());
                                    gpuDeviceInfo.put("manufacturer", g.manufacturer());
                                    gpuDeviceInfo.put("count", g.count());
                                    gpuDeviceInfo.put("gpuSizeInMiB", g.memoryInfo().sizeInMiB());
                                    return gpuDeviceInfo;
                                  })
                              .collect(Collectors.toList()));
                    }
                    attributes.put("gpuInfo", gpuInfoAttributes);
                  }

                  if (i.networkInfo() != null) {
                    attributes.put("ipv6Supported", i.networkInfo().ipv6Supported());
                  }

                  return new DefaultCacheData(
                      Keys.getInstanceTypeKey(i.instanceTypeAsString(), region, account.getName()),
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

  private List<InstanceTypeInfo> getInstanceTypes(Ec2Client ec2) {
    final List<InstanceTypeInfo> instanceTypeInfoList = new ArrayList<>();
    DescribeInstanceTypesRequest request = DescribeInstanceTypesRequest.builder().build();
    while (true) {
      final DescribeInstanceTypesResponse result = ec2.describeInstanceTypes(request);
      instanceTypeInfoList.addAll(result.instanceTypes());
      if (result.nextToken() != null) {
        request = request.toBuilder().nextToken(result.nextToken()).build();
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
