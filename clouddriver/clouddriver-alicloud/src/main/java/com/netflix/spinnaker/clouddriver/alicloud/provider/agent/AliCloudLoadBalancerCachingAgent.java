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
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.slb.model.v20140515.*;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancerAttributeResponse.ListenerPortAndProtocal;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancersResponse.LoadBalancer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.provider.AliProvider;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudClientProvider;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentials;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentialsProvider;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import java.util.*;
import org.apache.commons.lang3.StringUtils;

public class AliCloudLoadBalancerCachingAgent implements CachingAgent, AccountAware, OnDemandAgent {

  private AliProvider aliProvider;
  private AliCloudClientProvider aliCloudClientProvider;
  private AliCloudCredentials account;
  private String region;
  private AliCloudCredentialsProvider aliCloudCredentialsProvider;
  ObjectMapper objectMapper;
  OnDemandMetricsSupport metricsSupport;
  IAcsClient client;

  public AliCloudLoadBalancerCachingAgent(
      AliProvider aliProvider,
      String region,
      AliCloudClientProvider aliCloudClientProvider,
      AliCloudCredentialsProvider aliCloudCredentialsProvider,
      AliCloudProvider aliCloudProvider,
      ObjectMapper objectMapper,
      Registry registry,
      AliCloudCredentials credentials,
      IAcsClient client) {
    this.account = credentials;
    this.aliProvider = aliProvider;
    this.region = region;
    this.aliCloudClientProvider = aliCloudClientProvider;
    this.aliCloudCredentialsProvider = aliCloudCredentialsProvider;
    this.objectMapper = objectMapper;
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry,
            this,
            aliCloudProvider.getId()
                + ":"
                + aliCloudProvider.getId()
                + ":"
                + OnDemandAgent.OnDemandType.LoadBalancer);
    this.client = client;
  }

  static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          new ArrayList<AgentDataType>() {
            {
              add(AUTHORITATIVE.forType(LOAD_BALANCERS.ns));
              add(INFORMATIVE.forType(INSTANCES.ns));
            }
          });

  @Override
  public String getAccountName() {
    return null;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    List<LoadBalancer> loadBalancers = new ArrayList<>();
    Map<String, DescribeLoadBalancerAttributeResponse> loadBalancerAttributes = new HashMap<>(16);

    DescribeLoadBalancersRequest queryRequest = new DescribeLoadBalancersRequest();
    DescribeLoadBalancersResponse queryResponse;
    try {
      queryResponse = client.getAcsResponse(queryRequest);
      if (queryResponse.getLoadBalancers().isEmpty()) {
        return new DefaultCacheResult(new HashMap<>(16));
      }

      loadBalancers.addAll(queryResponse.getLoadBalancers());

    } catch (ServerException e) {
      e.printStackTrace();
    } catch (ClientException e) {
      e.printStackTrace();
    }

    for (LoadBalancer loadBalancer : loadBalancers) {

      DescribeLoadBalancerAttributeRequest describeLoadBalancerAttributeRequest =
          new DescribeLoadBalancerAttributeRequest();
      describeLoadBalancerAttributeRequest.setLoadBalancerId(loadBalancer.getLoadBalancerId());
      DescribeLoadBalancerAttributeResponse describeLoadBalancerAttributeResponse;
      try {
        describeLoadBalancerAttributeResponse =
            client.getAcsResponse(describeLoadBalancerAttributeRequest);
        loadBalancerAttributes.put(
            loadBalancer.getLoadBalancerName(), describeLoadBalancerAttributeResponse);

      } catch (ServerException e) {
        e.printStackTrace();
      } catch (ClientException e) {
        e.printStackTrace();
      }
    }

    return buildCacheResult(loadBalancers, loadBalancerAttributes, client);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    List<LoadBalancer> loadBalancers = new ArrayList<>();
    Map<String, DescribeLoadBalancerAttributeResponse> loadBalancerAttributes = new HashMap<>(16);

    DescribeLoadBalancersRequest queryRequest = new DescribeLoadBalancersRequest();
    queryRequest.setLoadBalancerName((String) data.get("loadBalancerName"));
    DescribeLoadBalancersResponse queryResponse;
    String loadBalancerId = null;

    queryResponse =
        metricsSupport.readData(
            () -> {
              try {
                return client.getAcsResponse(queryRequest);
              } catch (ServerException e) {
                e.printStackTrace();
              } catch (ClientException e) {
                e.printStackTrace();
              }
              return null;
            });

    loadBalancers.addAll(queryResponse.getLoadBalancers());

    if (StringUtils.isEmpty(loadBalancerId)) {
      return null;
    }

    DescribeLoadBalancerAttributeRequest describeLoadBalancerAttributeRequest =
        new DescribeLoadBalancerAttributeRequest();
    describeLoadBalancerAttributeRequest.setLoadBalancerId(loadBalancerId);
    DescribeLoadBalancerAttributeResponse describeLoadBalancerAttributeResponse;
    describeLoadBalancerAttributeResponse =
        metricsSupport.readData(
            () -> {
              try {
                return client.getAcsResponse(describeLoadBalancerAttributeRequest);

              } catch (ServerException e) {
                e.printStackTrace();
              } catch (ClientException e) {
                e.printStackTrace();
              }
              return null;
            });

    loadBalancerAttributes.put(
        describeLoadBalancerAttributeResponse.getLoadBalancerName(),
        describeLoadBalancerAttributeResponse);

    CacheResult cacheResult = buildCacheResult(loadBalancers, loadBalancerAttributes, client);

    if (cacheResult.getCacheResults().values().isEmpty()) {
      providerCache.evictDeletedItems(
          ON_DEMAND.ns,
          Lists.newArrayList(
              Keys.getLoadBalancerKey(
                  (String) data.get("loadBalancerName"),
                  account.getName(),
                  region,
                  (String) data.get("vpcId"))));
    } else {
      metricsSupport.onDemandStore(
          () -> {
            Map<String, Object> map = Maps.newHashMap();
            map.put("cacheTime", new Date());
            try {
              map.put(
                  "cacheResults", objectMapper.writeValueAsString(cacheResult.getCacheResults()));
            } catch (JsonProcessingException exception) {
              exception.printStackTrace();
            }

            CacheData cacheData =
                new DefaultCacheData(
                    Keys.getLoadBalancerKey(
                        (String) data.get("loadBalancerName"),
                        account.getName(),
                        region,
                        (String) data.get("vpcId")),
                    map,
                    Maps.newHashMap());

            providerCache.putCacheData(ON_DEMAND.ns, cacheData);
            return null;
          });
    }

    OnDemandResult result = new OnDemandResult(getAgentType(), cacheResult, null);

    return result;
  }

  private CacheResult buildCacheResult(
      Collection<LoadBalancer> loadBalancers,
      Map<String, DescribeLoadBalancerAttributeResponse> loadBalancerAttributes,
      IAcsClient client) {

    Map<String, Collection<CacheData>> cacheResults = new HashMap<>(16);
    List list = new ArrayList();

    for (LoadBalancer loadBalancer : loadBalancers) {
      String loadBalancerName = loadBalancer.getLoadBalancerName();
      Map<String, Object> map = objectMapper.convertValue(loadBalancer, Map.class);
      map.put("account", account.getName());

      DescribeLoadBalancerAttributeResponse describeLoadBalancerAttributeResponse =
          loadBalancerAttributes.get(loadBalancerName);
      Map<String, Object> attributeMap =
          objectMapper.convertValue(describeLoadBalancerAttributeResponse, Map.class);

      List<Map> listenerPortsAndProtocal = new ArrayList<>();
      for (ListenerPortAndProtocal listenerPortAndProtocal :
          describeLoadBalancerAttributeResponse.getListenerPortsAndProtocal()) {
        Integer listenerPort = listenerPortAndProtocal.getListenerPort();
        String listenerProtocal = listenerPortAndProtocal.getListenerProtocal().toUpperCase();
        Map<String, Object> portAndProtocalMap =
            objectMapper.convertValue(listenerPortAndProtocal, Map.class);

        Map<String, Object> listenerMap = new HashMap<>(16);

        switch (listenerProtocal) {
          case "HTTPS":
            DescribeLoadBalancerHTTPSListenerAttributeRequest httpsListenerAttributeRequest =
                new DescribeLoadBalancerHTTPSListenerAttributeRequest();
            httpsListenerAttributeRequest.setListenerPort(listenerPort);
            httpsListenerAttributeRequest.setLoadBalancerId(loadBalancer.getLoadBalancerId());
            DescribeLoadBalancerHTTPSListenerAttributeResponse httpsListenerAttributeResponse;
            try {
              httpsListenerAttributeResponse = client.getAcsResponse(httpsListenerAttributeRequest);
              listenerMap = objectMapper.convertValue(httpsListenerAttributeResponse, Map.class);
            } catch (ServerException e) {
              e.printStackTrace();
            } catch (ClientException e) {
              e.printStackTrace();
            }

            break;
          case "TCP":
            DescribeLoadBalancerTCPListenerAttributeRequest tcpListenerAttributeRequest =
                new DescribeLoadBalancerTCPListenerAttributeRequest();
            tcpListenerAttributeRequest.setListenerPort(listenerPort);
            tcpListenerAttributeRequest.setLoadBalancerId(loadBalancer.getLoadBalancerId());
            DescribeLoadBalancerTCPListenerAttributeResponse tcpListenerAttributeResponse;
            try {
              tcpListenerAttributeResponse = client.getAcsResponse(tcpListenerAttributeRequest);
              listenerMap = objectMapper.convertValue(tcpListenerAttributeResponse, Map.class);
            } catch (ServerException e) {
              e.printStackTrace();
            } catch (ClientException e) {
              e.printStackTrace();
            }

            break;
          case "UDP":
            DescribeLoadBalancerUDPListenerAttributeRequest udpListenerAttributeRequest =
                new DescribeLoadBalancerUDPListenerAttributeRequest();
            udpListenerAttributeRequest.setListenerPort(listenerPort);
            udpListenerAttributeRequest.setLoadBalancerId(loadBalancer.getLoadBalancerId());
            DescribeLoadBalancerUDPListenerAttributeResponse udpListenerAttributeResponse;
            try {
              udpListenerAttributeResponse = client.getAcsResponse(udpListenerAttributeRequest);
              listenerMap = objectMapper.convertValue(udpListenerAttributeResponse, Map.class);
            } catch (ServerException e) {
              e.printStackTrace();
            } catch (ClientException e) {
              e.printStackTrace();
            }

            break;
          default:
            DescribeLoadBalancerHTTPListenerAttributeRequest httpListenerAttributeRequest =
                new DescribeLoadBalancerHTTPListenerAttributeRequest();
            httpListenerAttributeRequest.setListenerPort(listenerPort);
            httpListenerAttributeRequest.setLoadBalancerId(loadBalancer.getLoadBalancerId());
            DescribeLoadBalancerHTTPListenerAttributeResponse httpListenerAttributeResponse;
            try {
              httpListenerAttributeResponse = client.getAcsResponse(httpListenerAttributeRequest);
              listenerMap = objectMapper.convertValue(httpListenerAttributeResponse, Map.class);
            } catch (ServerException e) {
              e.printStackTrace();
            } catch (ClientException e) {
              e.printStackTrace();
            }
            break;
        }
        listenerMap.putAll(portAndProtocalMap);
        listenerPortsAndProtocal.add(listenerMap);
      }

      attributeMap.put("listenerPortsAndProtocal", listenerPortsAndProtocal);
      map.put("attributes", attributeMap);
      DescribeVServerGroupsRequest describeVServerGroupsRequest =
          new DescribeVServerGroupsRequest();
      describeVServerGroupsRequest.setLoadBalancerId(loadBalancer.getLoadBalancerId());
      try {
        DescribeVServerGroupsResponse describeVServerGroupsResponse =
            client.getAcsResponse(describeVServerGroupsRequest);
        List<DescribeVServerGroupsResponse.VServerGroup> vServerGroups =
            describeVServerGroupsResponse.getVServerGroups();
        map.put("vServerGroups", vServerGroups);
      } catch (ServerException e) {
        e.printStackTrace();
      } catch (ClientException e) {
        e.printStackTrace();
      }
      list.add(
          new DefaultCacheData(
              Keys.getLoadBalancerKey(
                  loadBalancerName, account.getName(), region, loadBalancer.getVpcId()),
              map,
              Maps.newHashMap()));
    }
    cacheResults.put(LOAD_BALANCERS.ns, list);

    CacheResult cacheResult = new DefaultCacheResult(cacheResults);

    return cacheResult;
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
    return this.getAgentType() + "-OnDemand";
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return null;
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return false;
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return null;
  }
}
