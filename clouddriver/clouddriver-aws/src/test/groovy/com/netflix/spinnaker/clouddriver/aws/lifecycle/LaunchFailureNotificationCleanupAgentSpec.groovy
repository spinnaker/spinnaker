/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.lifecycle

import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.tags.EntityTagger
import com.netflix.spinnaker.credentials.CredentialsRepository
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.Activity
import software.amazon.awssdk.services.autoscaling.model.AutoScalingException
import software.amazon.awssdk.services.autoscaling.model.DescribeScalingActivitiesResponse
import spock.lang.Specification
import spock.lang.Unroll

class LaunchFailureNotificationCleanupAgentSpec extends Specification {
  static final LAUNCH_FAILURE_TAG_NAME = "spinnaker_ui_alert:autoscaling:ec2_instance_launch_error"

  def serverGroupTagger = Mock(EntityTagger)
  def amazonAutoScaling = Mock(AutoScalingClient)
  def credentialsRepository = Stub(CredentialsRepository) {
    getOne(_) >> { String name ->
      TestCredential.named(name)
    }
  }

  void "should delete launch failure notification tag if server group has no launch failures"() {
    given:
    def agent = new LaunchFailureNotificationCleanupAgent(
      Mock(AmazonClientProvider), credentialsRepository, serverGroupTagger
    ) {
      @Override
      protected boolean hasLaunchFailures(AutoScalingClient amazonAutoScaling, EntityTags entityTags) {
        return entityTags.entityRef.attributes().get("hasLaunchFailures")
      }
    }

    when:
    agent.run()

    then:
    1 * serverGroupTagger.delete("aws", "account1", "us-west-2", "servergroup", "test-v002", LAUNCH_FAILURE_TAG_NAME)
    1 * serverGroupTagger.taggedEntities("aws", null, "servergroup", LAUNCH_FAILURE_TAG_NAME, 10000) >> {
      return [
        new EntityTags(id: "1", entityRef: new EntityTags.EntityRef(
          accountId: "account1",
          account: "test",
          region: "us-west-2",
          entityId: "test-v001",
          attributes: ["hasLaunchFailures": true])
        ),
        new EntityTags(id: "2", entityRef: new EntityTags.EntityRef(
          accountId: "account1",
          account: "test",
          region: "us-west-2",
          entityId: "test-v002",
          attributes: ["hasLaunchFailures": false])
        )
      ]
    }
    0 * serverGroupTagger._
  }

  @Unroll
  void "should check scaling activities to determine if server group has launch failures"() {
    given:
    def entityTags = new EntityTags(entityRef: new EntityTags.EntityRef(account: "test", entityId: "test-v002"))
    def agent = new LaunchFailureNotificationCleanupAgent(
      Mock(AmazonClientProvider), credentialsRepository, Mock(EntityTagger)
    )

    when:
    def hasLaunchFailures = agent.hasLaunchFailures(amazonAutoScaling, entityTags)

    then:
    hasLaunchFailures == expectedLaunchFailures

    1 * amazonAutoScaling.describeScalingActivities(_) >> {
      DescribeScalingActivitiesResponse.builder().activities(activities).build()
    }

    where:
    activities                                                        || expectedLaunchFailures
    []                                                                || false
    [activity("Successful"), activity("Failed"), activity("Pending")] || false
    [activity("Failed"), activity("Successful")]                      || true
  }

  @Unroll
  void "should have no launch failures if server group does not exist"() {
    given:
    def entityTags = new EntityTags(entityRef: new EntityTags.EntityRef(account: "test", entityId: "test-v002"))
    def agent = new LaunchFailureNotificationCleanupAgent(
      Mock(AmazonClientProvider), credentialsRepository, Mock(EntityTagger)
    )

    and:
    1 * amazonAutoScaling.describeScalingActivities(_) >> {
      throw AutoScalingException.builder()
        .awsErrorDetails(AwsErrorDetails.builder().errorMessage(errorMessage).build())
        .build()
    }

    expect:
    try {
      def hasLaunchFailures = agent.hasLaunchFailures(amazonAutoScaling, entityTags)
      assert !hasLaunchFailures
      assert !expectedException
    } catch (Exception ignored) {
      assert expectedException
    }

    where:
    errorMessage                      || expectedException
    "AutoScalingGroup name not found" || false
    "Some random message"             || true
  }

  private static Activity activity(String statusCode) {
    return Activity.builder().statusCode(statusCode).build()
  }
}
