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

import com.aliyuncs.ecs.model.v20140526.DeleteSecurityGroupResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse.SecurityGroup;
import com.aliyuncs.exceptions.ClientException;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.DeleteAliCloudSecurityGroupDescription;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class DeleteAliCloudSecurityGroupAtomicOperationTest extends CommonAtomicOperation {

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any()))
        .thenAnswer(new DescribeSecurityGroupsAnswer())
        .thenAnswer(new DeleteSecurityGroupAnswer());
  }

  @Test
  public void testOperate() {
    DeleteAliCloudSecurityGroupAtomicOperation operation =
        new DeleteAliCloudSecurityGroupAtomicOperation(buildDescription(), clientFactory);
    operation.operate(priorOutputs);
  }

  private DeleteAliCloudSecurityGroupDescription buildDescription() {
    DeleteAliCloudSecurityGroupDescription description =
        new DeleteAliCloudSecurityGroupDescription();
    description.setCredentials(credentials);
    description.setSecurityGroupName("test-SecurityGroupName");
    List<String> regions = new ArrayList<>();
    regions.add(REGION);
    description.setRegions(regions);
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

  private class DeleteSecurityGroupAnswer implements Answer<DeleteSecurityGroupResponse> {
    @Override
    public DeleteSecurityGroupResponse answer(InvocationOnMock invocation) throws Throwable {
      DeleteSecurityGroupResponse response = new DeleteSecurityGroupResponse();
      return response;
    }
  }
}
