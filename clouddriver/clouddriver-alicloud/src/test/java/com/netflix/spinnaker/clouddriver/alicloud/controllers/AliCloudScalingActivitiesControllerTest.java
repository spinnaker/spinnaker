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
package com.netflix.spinnaker.clouddriver.alicloud.controllers;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aliyuncs.ess.model.v20140828.DescribeScalingActivitiesResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingActivitiesResponse.ScalingActivity;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse.ScalingGroup;
import com.aliyuncs.exceptions.ClientException;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.ops.CommonAtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.http.ResponseEntity;

public class AliCloudScalingActivitiesControllerTest extends CommonAtomicOperation {

  private final String SERVERGROUPNAME = "test-serverGroupName";

  static AccountCredentialsProvider accountCredentialsProvider =
      mock(AccountCredentialsProvider.class);

  @Before
  public void testBefore() throws ClientException {
    when(accountCredentialsProvider.getCredentials(anyString())).thenReturn(credentials);
    when(client.getAcsResponse(any()))
        .thenAnswer(new DescribeScalingGroupsAnswer())
        .thenAnswer(new DescribeScalingActivitiesAnswer());
  }

  @Test
  public void testGetScalingActivities() {
    AliCloudScalingActivitiesController controller =
        new AliCloudScalingActivitiesController(accountCredentialsProvider, clientFactory);
    ResponseEntity scalingActivities =
        controller.getScalingActivities(ACCOUNT, SERVERGROUPNAME, REGION);
    assertTrue(scalingActivities != null);
  }

  private class DescribeScalingGroupsAnswer implements Answer<DescribeScalingGroupsResponse> {
    @Override
    public DescribeScalingGroupsResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeScalingGroupsResponse response = new DescribeScalingGroupsResponse();
      List<ScalingGroup> scalingGroups = new ArrayList<>();
      ScalingGroup scalingGroup = new ScalingGroup();
      scalingGroup.setScalingGroupId("test-ID");
      scalingGroups.add(scalingGroup);
      response.setScalingGroups(scalingGroups);
      return response;
    }
  }

  private class DescribeScalingActivitiesAnswer
      implements Answer<DescribeScalingActivitiesResponse> {
    @Override
    public DescribeScalingActivitiesResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeScalingActivitiesResponse response = new DescribeScalingActivitiesResponse();
      List<ScalingActivity> scalingActivities = new ArrayList<>();
      ScalingActivity scalingActivity = new ScalingActivity();
      scalingActivity.setStatusCode("test-statu");
      scalingActivities.add(scalingActivity);
      response.setScalingActivities(scalingActivities);
      return response;
    }
  }
}
