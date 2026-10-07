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

import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.Activity
import software.amazon.awssdk.services.autoscaling.model.DescribeScalingActivitiesRequest
import software.amazon.awssdk.services.autoscaling.model.DescribeScalingActivitiesResponse
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.credentials.CredentialsRepository
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.Subject

class AmazonClusterControllerSpec extends Specification {

  @Subject controller = new AmazonClusterController()

  void "should perform real-time AWS call for auto-scaling activities"() {
    setup:
    def creds = Stub(NetflixAmazonCredentials)
    def credsProvider = Stub(CredentialsRepository) {
      getOne(account) >> creds
    }
    def autoScaling = Mock(AutoScalingClient)
    def provider = Stub(AmazonClientProvider)
    provider.getAutoScalingV2(creds, region) >> autoScaling
    controller.amazonClientProvider = provider
    controller.credentialsRepository = credsProvider

    when:
    def result = controller.getScalingActivities(account, asgName, region)

    then:
    1 * autoScaling.describeScalingActivities(_) >> { DescribeScalingActivitiesRequest request ->
      assert request.autoScalingGroupName() == asgName
      DescribeScalingActivitiesResponse.builder().activities(Activity.builder().activityId("1234").build()).build()
    }
    result.statusCode == HttpStatus.OK
    result.body instanceof List
    result.body[0].activityId() == "1234"

    where:
    account = "test"
    asgName = "kato-main-v000"
    region  = "us-east-1"
  }
}
