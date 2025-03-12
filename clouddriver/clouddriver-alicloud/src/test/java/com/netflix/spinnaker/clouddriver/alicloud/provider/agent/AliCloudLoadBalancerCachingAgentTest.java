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

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancerAttributeResponse;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancerAttributeResponse.ListenerPortAndProtocal;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancerHTTPListenerAttributeResponse;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancersResponse;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancersResponse.LoadBalancer;
import com.aliyuncs.slb.model.v20140515.DescribeVServerGroupsResponse;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.provider.AliProvider;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudClientProvider;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentialsProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import spock.lang.Subject;

public class AliCloudLoadBalancerCachingAgentTest extends CommonCachingAgentTest {

  private final String NAME = "lbName";
  private final String ID = "lbId";

  @Subject AliProvider aliProvider = mock(AliProvider.class);

  @Subject AliCloudClientProvider aliCloudClientProvider = mock(AliCloudClientProvider.class);

  @Subject
  AliCloudCredentialsProvider aliCloudCredentialsProvider = mock(AliCloudCredentialsProvider.class);

  @Subject AliCloudProvider aliCloudProvider = mock(AliCloudProvider.class);

  @Subject Registry registry = mock(Registry.class);

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any()))
        .thenAnswer(new LoadBalancersAnswer())
        .thenAnswer(new LoadBalancerAttributeAnswer())
        .thenAnswer(new HTTPSListenerAnswer())
        .thenAnswer(new DescribeVServerGroupsResponseAnswer());
  }

  @Test
  public void testLoadData() {
    AliCloudLoadBalancerCachingAgent agent =
        new AliCloudLoadBalancerCachingAgent(
            aliProvider,
            REGION,
            aliCloudClientProvider,
            aliCloudCredentialsProvider,
            aliCloudProvider,
            objectMapper,
            registry,
            account,
            client);
    CacheResult result = agent.loadData(providerCache);
    String key = Keys.getLoadBalancerKey(NAME, ACCOUNT, REGION, null);
    List<CacheData> LoadBalancers = (List) result.getCacheResults().get(LOAD_BALANCERS.ns);
    assertTrue(LoadBalancers.size() == 1);
    assertTrue(key.equals(LoadBalancers.get(0).getId()));
  }

  private class LoadBalancersAnswer implements Answer<DescribeLoadBalancersResponse> {
    @Override
    public DescribeLoadBalancersResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeLoadBalancersResponse response = new DescribeLoadBalancersResponse();
      List<LoadBalancer> loadBalancers = new ArrayList<>();
      LoadBalancer loadBalancer = new LoadBalancer();
      loadBalancer.setLoadBalancerId(ID);
      loadBalancer.setLoadBalancerName(NAME);
      loadBalancers.add(loadBalancer);
      response.setLoadBalancers(loadBalancers);
      return response;
    }
  }

  private class LoadBalancerAttributeAnswer
      implements Answer<DescribeLoadBalancerAttributeResponse> {

    @Override
    public DescribeLoadBalancerAttributeResponse answer(InvocationOnMock invocation)
        throws Throwable {
      DescribeLoadBalancerAttributeResponse response = new DescribeLoadBalancerAttributeResponse();
      response.setLoadBalancerName(NAME);
      response.setLoadBalancerId(ID);
      List<ListenerPortAndProtocal> listenerPortsAndProtocal = new ArrayList<>();
      ListenerPortAndProtocal listenerPortAndProtocal = new ListenerPortAndProtocal();
      listenerPortAndProtocal.setListenerPort(80);
      listenerPortAndProtocal.setListenerProtocal("http");
      listenerPortsAndProtocal.add(listenerPortAndProtocal);
      response.setListenerPortsAndProtocal(listenerPortsAndProtocal);
      return response;
    }
  }

  private class HTTPSListenerAnswer
      implements Answer<DescribeLoadBalancerHTTPListenerAttributeResponse> {
    @Override
    public DescribeLoadBalancerHTTPListenerAttributeResponse answer(InvocationOnMock invocation)
        throws Throwable {
      DescribeLoadBalancerHTTPListenerAttributeResponse response =
          new DescribeLoadBalancerHTTPListenerAttributeResponse();
      response.setListenerPort(80);
      return response;
    }
  }

  private class DescribeVServerGroupsResponseAnswer
      implements Answer<DescribeVServerGroupsResponse> {

    @Override
    public DescribeVServerGroupsResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeVServerGroupsResponse describeVServerGroupsResponse =
          new DescribeVServerGroupsResponse();
      return describeVServerGroupsResponse;
    }
  }
}
