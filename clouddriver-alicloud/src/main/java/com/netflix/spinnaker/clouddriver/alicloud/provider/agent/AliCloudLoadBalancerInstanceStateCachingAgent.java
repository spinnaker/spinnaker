/*
 * Copyright 2019 Alibaba Group.
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
package com.netflix.spinnaker.clouddriver.alicloud.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.slb.model.v20140515.DescribeHealthStatusRequest;
import com.aliyuncs.slb.model.v20140515.DescribeHealthStatusResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.provider.AliProvider;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentials;
import com.netflix.spinnaker.clouddriver.core.provider.agent.HealthProvidingCachingAgent;
import java.util.*;
import org.springframework.context.ApplicationContext;

public class AliCloudLoadBalancerInstanceStateCachingAgent
    implements CachingAgent, HealthProvidingCachingAgent {
  AliCloudCredentials account;
  String region;
  ObjectMapper objectMapper;
  IAcsClient client;
  Cache cacheView;
  ApplicationContext ctx;
  static final String healthId = "alicloud-load-balancer-instance-health";

  public AliCloudLoadBalancerInstanceStateCachingAgent(
      ApplicationContext ctx,
      AliCloudCredentials account,
      String region,
      ObjectMapper objectMapper,
      IAcsClient client) {
    this.ctx = ctx;
    this.account = account;
    this.region = region;
    this.objectMapper = objectMapper;
    this.client = client;
  }

  static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          new ArrayList<AgentDataType>() {
            {
              add(AUTHORITATIVE.forType(HEALTH.ns));
            }
          });

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    Map<String, Collection<CacheData>> resultMap = new HashMap<>(16);
    List<CacheData> instanceDatas = new ArrayList<>();

    Collection<String> allLoadBalancerKeys = getCacheView().getIdentifiers(LOAD_BALANCERS.ns);
    Collection<CacheData> loadBalancerData =
        getCacheView().getAll(LOAD_BALANCERS.ns, allLoadBalancerKeys, null);
    DescribeHealthStatusRequest describeHealthStatusRequest = new DescribeHealthStatusRequest();
    DescribeHealthStatusResponse describeHealthStatusResponse;
    for (CacheData cacheData : loadBalancerData) {
      Map<String, Object> loadBalancerAttributes =
          objectMapper.convertValue(cacheData.getAttributes(), Map.class);
      String loadBalancerId = String.valueOf(loadBalancerAttributes.get("loadBalancerId"));
      describeHealthStatusRequest.setLoadBalancerId(loadBalancerId);
      String regionId = String.valueOf(loadBalancerAttributes.get("regionId"));
      describeHealthStatusRequest.setSysRegionId(regionId);
      try {
        describeHealthStatusResponse = client.getAcsResponse(describeHealthStatusRequest);
        for (DescribeHealthStatusResponse.BackendServer backendServer :
            describeHealthStatusResponse.getBackendServers()) {
          Map<String, Object> attributes = objectMapper.convertValue(backendServer, Map.class);
          attributes.put("loadBalancerId", loadBalancerId);
          CacheData data =
              new DefaultCacheData(
                  Keys.getInstanceHealthKey(
                      loadBalancerId,
                      backendServer.getServerId(),
                      backendServer.getListenerPort().toString(),
                      account.getName(),
                      regionId,
                      healthId),
                  attributes,
                  new HashMap<>(16));
          instanceDatas.add(data);
        }

      } catch (ServerException e) {
        e.printStackTrace();
      } catch (ClientException e) {
        e.printStackTrace();
      }
    }
    resultMap.put(HEALTH.ns, instanceDatas);

    return new DefaultCacheResult(resultMap);
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public String getAgentType() {
    return account.getName() + "/" + region + "/" + this.getClass().getSimpleName();
  }

  @Override
  public String getProviderName() {
    return AliProvider.PROVIDER_NAME;
  }

  @Override
  public String getHealthId() {
    return healthId;
  }

  private Cache getCacheView() {
    if (this.cacheView == null) {
      this.cacheView = ctx.getBean(Cache.class);
    }
    return this.cacheView;
  }
}
