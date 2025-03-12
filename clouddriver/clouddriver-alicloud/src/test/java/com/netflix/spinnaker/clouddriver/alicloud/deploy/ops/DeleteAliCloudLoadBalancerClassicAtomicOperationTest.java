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
package com.netflix.spinnaker.clouddriver.alicloud.deploy.ops;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.slb.model.v20140515.DeleteLoadBalancerResponse;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancersResponse;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.UpsertAliCloudLoadBalancerDescription;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class DeleteAliCloudLoadBalancerClassicAtomicOperationTest extends CommonAtomicOperation {

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any()))
        .thenAnswer(new DescribeLoadBalancersAnswer())
        .thenAnswer(new DeleteLoadBalancerAnswer());
  }

  @Test
  public void testOperate() {
    DeleteAliCloudLoadBalancerClassicAtomicOperation operation =
        new DeleteAliCloudLoadBalancerClassicAtomicOperation(buildDescription(), clientFactory);
    operation.operate(priorOutputs);
  }

  private UpsertAliCloudLoadBalancerDescription buildDescription() {
    UpsertAliCloudLoadBalancerDescription description = new UpsertAliCloudLoadBalancerDescription();
    description.setRegion(REGION);
    description.setCredentials(credentials);
    description.setLoadBalancerName("test-lbName");
    return description;
  }

  private class DescribeLoadBalancersAnswer implements Answer<DescribeLoadBalancersResponse> {
    @Override
    public DescribeLoadBalancersResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeLoadBalancersResponse response = new DescribeLoadBalancersResponse();
      response.setLoadBalancers(new ArrayList<>());
      return response;
    }
  }

  private class DeleteLoadBalancerAnswer implements Answer<DeleteLoadBalancerResponse> {
    @Override
    public DeleteLoadBalancerResponse answer(InvocationOnMock invocation) throws Throwable {
      return null;
    }
  }
}
