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

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupAttributeResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse.SecurityGroup;
import com.aliyuncs.exceptions.ClientException;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AliCloudSecurityGroupCachingAgentTest extends CommonCachingAgentTest {

  private final String NAME = "sgName";
  private final String ID = "sgId";
  private final String VPCID = "vpcId";

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any()))
        .thenAnswer(new SecurityGroupsAnswer())
        .thenAnswer(new SecurityGroupAttributeAnswer());
  }

  @Test
  public void testLoadData() {
    AliCloudSecurityGroupCachingAgent agent =
        new AliCloudSecurityGroupCachingAgent(account, REGION, objectMapper, client);
    CacheResult result = agent.loadData(providerCache);
    String key = Keys.getSecurityGroupKey(NAME, ID, REGION, ACCOUNT, VPCID);
    List<CacheData> sg = (List) result.getCacheResults().get(Keys.Namespace.SECURITY_GROUPS.ns);
    assertTrue(sg.size() == 1);
    assertTrue(key.equals(sg.get(0).getId()));
  }

  private class SecurityGroupsAnswer implements Answer<DescribeSecurityGroupsResponse> {
    @Override
    public DescribeSecurityGroupsResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeSecurityGroupsResponse response = new DescribeSecurityGroupsResponse();
      List<SecurityGroup> securityGroups = new ArrayList<>();
      SecurityGroup securityGroup = new SecurityGroup();
      securityGroup.setSecurityGroupName(NAME);
      securityGroup.setSecurityGroupId(ID);
      securityGroup.setVpcId(VPCID);
      securityGroups.add(securityGroup);
      response.setSecurityGroups(securityGroups);
      return response;
    }
  }

  private class SecurityGroupAttributeAnswer
      implements Answer<DescribeSecurityGroupAttributeResponse> {
    @Override
    public DescribeSecurityGroupAttributeResponse answer(InvocationOnMock invocation)
        throws Throwable {
      DescribeSecurityGroupAttributeResponse response =
          new DescribeSecurityGroupAttributeResponse();
      return response;
    }
  }
}
