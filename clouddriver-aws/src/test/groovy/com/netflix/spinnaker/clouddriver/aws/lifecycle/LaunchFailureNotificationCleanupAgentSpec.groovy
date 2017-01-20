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

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.Activity
import com.amazonaws.services.autoscaling.model.DescribeScalingActivitiesResult
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.tags.ServerGroupTagger
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.UndeclaredThrowableException;

class LaunchFailureNotificationCleanupAgentSpec extends Specification {
  static final LAUNCH_FAILURE_TAG_NAME = "spinnaker_ui_alert:autoscaling:ec2_instance_launch_error"

  def serverGroupTagger = Mock(ServerGroupTagger)
  def amazonAutoScaling = Mock(AmazonAutoScaling)

  void "should delete launch failure notification tag if server group has no launch failures"() {
    given:
    def agent = new LaunchFailureNotificationCleanupAgent(
      Mock(AmazonClientProvider), Mock(AccountCredentialsProvider), serverGroupTagger
    ) {
      @Override
      protected boolean hasLaunchFailures(AmazonAutoScaling amazonAutoScaling, EntityTags entityTags) {
        return entityTags.entityRef.attributes().get("hasLaunchFailures")
      }
    }

    when:
    agent.run()

    then:
    1 * serverGroupTagger.delete("aws", "account1", "us-west-2", "test-v002", LAUNCH_FAILURE_TAG_NAME)
    1 * serverGroupTagger.taggedEntities("aws", null, LAUNCH_FAILURE_TAG_NAME, 10000) >> {
      return [
        new EntityTags(id: "1", entityRef: new EntityTags.EntityRef(
          accountId: "account1",
          region: "us-west-2",
          entityId: "test-v001",
          attributes: ["hasLaunchFailures": true])
        ),
        new EntityTags(id: "2", entityRef: new EntityTags.EntityRef(
          accountId: "account1",
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
    def entityTags = new EntityTags(entityRef: new EntityTags.EntityRef(entityId: "test-v002"))
    def agent = new LaunchFailureNotificationCleanupAgent(
      Mock(AmazonClientProvider), Mock(AccountCredentialsProvider), Mock(ServerGroupTagger)
    )

    when:
    def hasLaunchFailures = agent.hasLaunchFailures(amazonAutoScaling, entityTags)

    then:
    hasLaunchFailures == expectedLaunchFailures

    1 * amazonAutoScaling.describeScalingActivities(_) >> {
      new DescribeScalingActivitiesResult().withActivities(activities)
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
    def entityTags = new EntityTags(entityRef: new EntityTags.EntityRef(entityId: "test-v002"))
    def agent = new LaunchFailureNotificationCleanupAgent(
      Mock(AmazonClientProvider), Mock(AccountCredentialsProvider), Mock(ServerGroupTagger)
    )

    and:
    1 * amazonAutoScaling.describeScalingActivities(_) >> {
      throw new UndeclaredThrowableException(
        new InvocationTargetException(
          new AmazonServiceException(errorMessage)
        )
      )
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
    return new Activity().withStatusCode(statusCode)
  }
}
