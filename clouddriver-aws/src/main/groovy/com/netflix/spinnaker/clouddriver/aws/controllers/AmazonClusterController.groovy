/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.controllers

import com.amazonaws.services.autoscaling.model.Activity
import com.amazonaws.services.autoscaling.model.DescribeScalingActivitiesRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/applications/{application}/clusters/{account}/{clusterName}/aws/serverGroups/{serverGroupName}")
class AmazonClusterController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AmazonClientProvider amazonClientProvider

  final static int MAX_SCALING_ACTIVITIES = 30

  @RequestMapping(value = "/scalingActivities", method = RequestMethod.GET)
  ResponseEntity getScalingActivities(@PathVariable String account, @PathVariable String serverGroupName, @RequestParam(value = "region", required = true) String region) {
    def credentials = accountCredentialsProvider.getCredentials(account)
    if (!(credentials instanceof NetflixAmazonCredentials)) {
      return new ResponseEntity([message: "bad credentials"], HttpStatus.BAD_REQUEST)
    }
    def autoScaling = amazonClientProvider.getAutoScaling(credentials, region)
    def request = new DescribeScalingActivitiesRequest(autoScalingGroupName: serverGroupName)
    def response = autoScaling.describeScalingActivities(request)
    List<Activity> scalingActivities = []
    while (scalingActivities.size() < MAX_SCALING_ACTIVITIES) {
      scalingActivities.addAll response.activities
      if (response.nextToken) {
        response = autoScaling.describeScalingActivities(request.withNextToken(response.nextToken))
      } else {
        break
      }
    }
    return new ResponseEntity(scalingActivities, HttpStatus.OK)
  }
}
