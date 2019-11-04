/*
 * Copyright 2019 Alibaba Group.
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
package com.netflix.spinnaker.clouddriver.alicloud.deploy.ops;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.slb.model.v20140515.CreateLoadBalancerResponse;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancersResponse;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.UpsertAliCloudLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.alicloud.model.alienum.ListenerType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class UpsertAliCloudLoadBalancerAtomicOperationTest extends CommonAtomicOperation {

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any()))
        .thenAnswer(new DescribeLoadBalancersAnswer())
        .thenAnswer(new CreateLoadBalancerAnswer());
  }

  @Test
  public void testOperate() {
    Map description = buildDescription();
    UpsertAliCloudLoadBalancerDescription upsertAliCloudLoadBalancerDescription =
        objectMapper.convertValue(description, UpsertAliCloudLoadBalancerDescription.class);
    upsertAliCloudLoadBalancerDescription.setCredentials(credentials);
    UpsertAliCloudLoadBalancerAtomicOperation operation =
        new UpsertAliCloudLoadBalancerAtomicOperation(
            upsertAliCloudLoadBalancerDescription, objectMapper, clientFactory);
    Map operate = operation.operate(priorOutputs);
    assertTrue(operate != null);
  }

  private Map buildDescription() {
    Map<String, Object> description = new HashMap<>();
    description.put("region", REGION);
    description.put("credentials", ACCOUNT);
    description.put("loadBalancerName", "test-loadBalancerName");
    List<Map> listeners = new ArrayList<>();
    Map<String, Object> listener = new HashMap<>();
    listener.put("listenerProtocal", ListenerType.HTTP);
    listener.put("healthCheckURI", "/test/index.html");
    listener.put("healthCheck", "on");
    listener.put("healthCheckTimeout", 5);
    listener.put("unhealthyThreshold", 3);
    listener.put("healthyThreshold", 3);
    listener.put("healthCheckInterval", 2);
    listener.put("listenerPort", 80);
    listener.put("bandwidth", 112);
    listener.put("stickySession", "off");
    listener.put("backendServerPort", 90);
    listeners.add(listener);
    description.put("listeners", listeners);
    description.put("vpcId", "vpc-test");
    description.put("vSwitchId", "111111");
    return description;
  }

  private class CreateLoadBalancerAnswer implements Answer<CreateLoadBalancerResponse> {
    @Override
    public CreateLoadBalancerResponse answer(InvocationOnMock invocation) throws Throwable {
      CreateLoadBalancerResponse response = new CreateLoadBalancerResponse();
      response.setLoadBalancerId("test-lbId");
      return response;
    }
  }

  private class DescribeLoadBalancersAnswer implements Answer<DescribeLoadBalancersResponse> {
    @Override
    public DescribeLoadBalancersResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeLoadBalancersResponse response = new DescribeLoadBalancersResponse();
      response.setLoadBalancers(new ArrayList<>());
      return response;
    }
  }
}
