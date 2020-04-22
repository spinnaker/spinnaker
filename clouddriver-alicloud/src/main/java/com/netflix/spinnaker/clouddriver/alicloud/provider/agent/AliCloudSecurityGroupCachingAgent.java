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

import com.aliyuncs.IAcsClient;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupAttributeRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupAttributeResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse.SecurityGroup;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.provider.AliProvider;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentials;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import java.util.*;

public class AliCloudSecurityGroupCachingAgent
    implements CachingAgent, OnDemandAgent, AccountAware {

  AliCloudCredentials account;
  String region;
  ObjectMapper objectMapper;
  IAcsClient client;

  public AliCloudSecurityGroupCachingAgent(
      AliCloudCredentials account, String region, ObjectMapper objectMapper, IAcsClient client) {
    this.account = account;
    this.region = region;
    this.objectMapper = objectMapper;
    this.client = client;
  }

  static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          new ArrayList<AgentDataType>() {
            {
              add(AUTHORITATIVE.forType(Keys.Namespace.SECURITY_GROUPS.ns));
            }
          });

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    Map<String, Collection<CacheData>> resultMap = new HashMap<>(16);
    List<CacheData> securityGroupDatas = new ArrayList<>();

    DescribeSecurityGroupsRequest securityGroupsRequest = new DescribeSecurityGroupsRequest();
    securityGroupsRequest.setPageSize(50);
    DescribeSecurityGroupsResponse securityGroupsResponse;

    try {
      securityGroupsResponse = client.getAcsResponse(securityGroupsRequest);
      for (SecurityGroup securityGroup : securityGroupsResponse.getSecurityGroups()) {
        securityGroupDatas.add(buildCatchData(securityGroup));
      }

    } catch (ServerException e) {
      e.printStackTrace();
    } catch (ClientException e) {
      e.printStackTrace();
    }

    resultMap.put(Keys.Namespace.SECURITY_GROUPS.ns, securityGroupDatas);

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
  public String getOnDemandAgentType() {
    return getAgentType();
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return null;
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return OnDemandAgent.OnDemandType.SecurityGroup.equals(type)
        && AliCloudProvider.ID.equals(cloudProvider);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (data.get("securityGroupName") == null) {
      return null;
    }
    String securityGroupName = (String) data.get("securityGroupName");

    DescribeSecurityGroupsRequest securityGroupsRequest = new DescribeSecurityGroupsRequest();
    securityGroupsRequest.setPageSize(50);
    securityGroupsRequest.setSecurityGroupName(securityGroupName);
    DescribeSecurityGroupsResponse securityGroupsResponse;

    try {
      securityGroupsResponse = client.getAcsResponse(securityGroupsRequest);
      if (securityGroupsResponse.getSecurityGroups().size() > 0) {
        SecurityGroup securityGroup = securityGroupsResponse.getSecurityGroups().get(0);
        CacheData cacheData = buildCatchData(securityGroup);
        providerCache.putCacheData(Keys.Namespace.SECURITY_GROUPS.ns, cacheData);
      }
    } catch (ServerException e) {
      e.printStackTrace();
    } catch (ClientException e) {
      e.printStackTrace();
    }
    return null;
  }

  CacheData buildCatchData(SecurityGroup securityGroup) throws ClientException, ServerException {
    Map<String, Object> attributes = objectMapper.convertValue(securityGroup, Map.class);
    attributes.put("provider", AliCloudProvider.ID);
    attributes.put("account", account.getName());
    attributes.put("regionId", region);

    DescribeSecurityGroupAttributeRequest securityGroupAttributeRequest =
        new DescribeSecurityGroupAttributeRequest();
    securityGroupAttributeRequest.setSecurityGroupId(securityGroup.getSecurityGroupId());
    securityGroupAttributeRequest.setDirection("ingress");
    DescribeSecurityGroupAttributeResponse securityGroupAttribute =
        client.getAcsResponse(securityGroupAttributeRequest);
    attributes.put("permissions", securityGroupAttribute.getPermissions());
    return new DefaultCacheData(
        Keys.getSecurityGroupKey(
            securityGroup.getSecurityGroupName(),
            securityGroup.getSecurityGroupId(),
            region,
            account.getName(),
            securityGroup.getVpcId()),
        attributes,
        new HashMap<>(16));
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    List<Map> resultList = new ArrayList<>();
    Map<String, Object> map = new HashMap<>();
    resultList.add(map);
    return resultList;
  }

  @Override
  public String getAccountName() {
    return account.getName();
  }
}
