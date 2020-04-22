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

import com.aliyuncs.ecs.model.v20140526.*;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupAttributeResponse.Permission;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse.SecurityGroup;
import com.aliyuncs.exceptions.ClientException;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.UpsertAliCloudSecurityGroupDescription;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class UpsertAliCloudSecurityGroupAtomicOperationTest extends CommonAtomicOperation {

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any()))
        .thenAnswer(new DescribeSecurityGroupsAnswer())
        .thenAnswer(new CreateSecurityGroupAnswer())
        .thenAnswer(new DescribeSecurityGroupAttributeAnswer())
        .thenAnswer(new AuthorizeSecurityGroupAnswer());
  }

  @Test
  public void testOperate() {
    UpsertAliCloudSecurityGroupAtomicOperation operation =
        new UpsertAliCloudSecurityGroupAtomicOperation(
            buildDescription(), clientFactory, objectMapper);
    operation.operate(priorOutputs);
  }

  private UpsertAliCloudSecurityGroupDescription buildDescription() {
    UpsertAliCloudSecurityGroupDescription description =
        new UpsertAliCloudSecurityGroupDescription();
    description.setRegion(REGION);
    description.setCredentials(credentials);
    description.setSecurityGroupName("test-SecurityGroupName");
    List<AuthorizeSecurityGroupRequest> securityGroupIngress = new ArrayList<>();
    AuthorizeSecurityGroupRequest request = new AuthorizeSecurityGroupRequest();
    request.setIpProtocol("tcp");
    request.setPortRange("1/200");
    request.setSourceCidrIp("10.0.0.0/8");
    securityGroupIngress.add(request);
    description.setSecurityGroupIngress(securityGroupIngress);
    return description;
  }

  private class DescribeSecurityGroupsAnswer implements Answer<DescribeSecurityGroupsResponse> {
    @Override
    public DescribeSecurityGroupsResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeSecurityGroupsResponse response = new DescribeSecurityGroupsResponse();
      List<SecurityGroup> securityGroups = new ArrayList<>();
      response.setSecurityGroups(securityGroups);
      return response;
    }
  }

  private class CreateSecurityGroupAnswer implements Answer<CreateSecurityGroupResponse> {
    @Override
    public CreateSecurityGroupResponse answer(InvocationOnMock invocation) throws Throwable {
      CreateSecurityGroupResponse response = new CreateSecurityGroupResponse();
      response.setSecurityGroupId("test-SecurityGroupId");
      return response;
    }
  }

  private class DescribeSecurityGroupAttributeAnswer
      implements Answer<DescribeSecurityGroupAttributeResponse> {
    @Override
    public DescribeSecurityGroupAttributeResponse answer(InvocationOnMock invocation)
        throws Throwable {
      DescribeSecurityGroupAttributeResponse response =
          new DescribeSecurityGroupAttributeResponse();
      List<Permission> permissions = new ArrayList<>();
      response.setPermissions(permissions);
      return response;
    }
  }

  private class AuthorizeSecurityGroupAnswer implements Answer<AuthorizeSecurityGroupResponse> {
    @Override
    public AuthorizeSecurityGroupResponse answer(InvocationOnMock invocation) throws Throwable {
      AuthorizeSecurityGroupResponse response = new AuthorizeSecurityGroupResponse();
      return response;
    }
  }
}
