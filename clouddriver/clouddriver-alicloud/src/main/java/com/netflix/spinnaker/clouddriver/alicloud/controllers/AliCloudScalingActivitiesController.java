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

import com.aliyuncs.IAcsClient;
import com.aliyuncs.ess.model.v20140828.DescribeScalingActivitiesRequest;
import com.aliyuncs.ess.model.v20140828.DescribeScalingActivitiesResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingActivitiesResponse.ScalingActivity;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsRequest;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse.ScalingGroup;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.netflix.spinnaker.clouddriver.alicloud.common.ClientFactory;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
    "/applications/{application}/clusters/{account}/{clusterName}/alicloud/serverGroups/{serverGroupName}")
public class AliCloudScalingActivitiesController {

  private final AccountCredentialsProvider accountCredentialsProvider;

  private final ClientFactory clientFactory;

  @Autowired
  public AliCloudScalingActivitiesController(
      AccountCredentialsProvider accountCredentialsProvider, ClientFactory clientFactory) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.clientFactory = clientFactory;
  }

  @RequestMapping(value = "/scalingActivities", method = RequestMethod.GET)
  ResponseEntity getScalingActivities(
      @PathVariable String account,
      @PathVariable String serverGroupName,
      @RequestParam(value = "region", required = true) String region) {
    List<ScalingActivity> resultList = new ArrayList<>();
    AccountCredentials credentials = accountCredentialsProvider.getCredentials(account);
    if (!(credentials instanceof AliCloudCredentials)) {
      Map<String, String> messageMap = new HashMap<>();
      messageMap.put("message", "bad credentials");
      return new ResponseEntity(messageMap, HttpStatus.BAD_REQUEST);
    }
    AliCloudCredentials aliCloudCredentials = (AliCloudCredentials) credentials;
    IAcsClient client =
        clientFactory.createClient(
            region, aliCloudCredentials.getAccessKeyId(), aliCloudCredentials.getAccessSecretKey());
    DescribeScalingGroupsRequest describeScalingGroupsRequest = new DescribeScalingGroupsRequest();
    describeScalingGroupsRequest.setScalingGroupName(serverGroupName);
    describeScalingGroupsRequest.setPageSize(50);
    DescribeScalingGroupsResponse describeScalingGroupsResponse;
    try {
      describeScalingGroupsResponse = client.getAcsResponse(describeScalingGroupsRequest);
      if (describeScalingGroupsResponse.getScalingGroups().size() > 0) {
        ScalingGroup scalingGroup = describeScalingGroupsResponse.getScalingGroups().get(0);
        DescribeScalingActivitiesRequest activitiesRequest = new DescribeScalingActivitiesRequest();
        activitiesRequest.setScalingGroupId(scalingGroup.getScalingGroupId());
        activitiesRequest.setPageSize(50);
        DescribeScalingActivitiesResponse activitiesResponse =
            client.getAcsResponse(activitiesRequest);
        resultList.addAll(activitiesResponse.getScalingActivities());
      }

    } catch (ServerException e) {
      e.printStackTrace();
      throw new IllegalStateException(e.getMessage());
    } catch (ClientException e) {
      e.printStackTrace();
      throw new IllegalStateException(e.getMessage());
    }
    return new ResponseEntity(resultList, HttpStatus.OK);
  }
}
