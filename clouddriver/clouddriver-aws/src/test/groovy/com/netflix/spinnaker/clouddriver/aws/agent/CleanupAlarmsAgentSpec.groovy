/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.aws.agent

import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.Alarm
import software.amazon.awssdk.services.autoscaling.model.DescribePoliciesResponse
import software.amazon.awssdk.services.autoscaling.model.ScalingPolicy
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.DeleteAlarmsRequest
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.credentials.CredentialsRepository
import org.joda.time.DateTime
import spock.lang.Shared
import spock.lang.Specification

class CleanupAlarmsAgentSpec extends Specification {

  @Shared
  def test = TestCredential.named('test')

  AutoScalingClient autoScalingUSW
  AutoScalingClient autoScalingUSE
  CloudWatchClient cloudWatchUSW
  CloudWatchClient cloudWatchUSE
  AmazonClientProvider amazonClientProvider
  CredentialsRepository credentialsRepository
  CleanupAlarmsAgent agent
  String validUuid = UUID.randomUUID().toString()
  String deletableAlarmName = "clouddriver-test-v123-alarm-" + validUuid

  void setup() {
    autoScalingUSW = Mock(AutoScalingClient)
    autoScalingUSE = Mock(AutoScalingClient)
    cloudWatchUSW = Mock(CloudWatchClient)
    cloudWatchUSE = Mock(CloudWatchClient)

    amazonClientProvider = Mock(AmazonClientProvider) {
      1 * getAutoScalingV2(test, "us-west-1") >> autoScalingUSW
      1 * getAutoScalingV2(test, "us-east-1") >> autoScalingUSE
      1 * getAmazonCloudWatchV2(test, "us-west-1") >> cloudWatchUSW
      1 * getAmazonCloudWatchV2(test, "us-east-1") >> cloudWatchUSE
      0 * _
    }

    credentialsRepository = Mock(CredentialsRepository) {
      1 * getAll() >> [test]
      0 * _
    }

    agent = new CleanupAlarmsAgent(amazonClientProvider, credentialsRepository, 10L, 10L, 90, ".+-v[0-9]{3}-alarm-.+")
  }

  void "should run across all regions/accounts and delete in each"() {
    when:
    agent.run()

    then:
    1 * autoScalingUSW.describePolicies(_) >> DescribePoliciesResponse.builder().build()
    1 * autoScalingUSE.describePolicies(_) >> DescribePoliciesResponse.builder().build()
    1 * cloudWatchUSE.describeAlarms(_) >> DescribeAlarmsResponse.builder().metricAlarms([buildAlarm(deletableAlarmName, 92)]).build()
    1 * cloudWatchUSW.describeAlarms(_) >> DescribeAlarmsResponse.builder().metricAlarms([buildAlarm(deletableAlarmName, 92)]).build()
    1 * cloudWatchUSE.deleteAlarms({ DeleteAlarmsRequest r -> r.alarmNames() == [deletableAlarmName]} as DeleteAlarmsRequest)
    1 * cloudWatchUSW.deleteAlarms({ DeleteAlarmsRequest r -> r.alarmNames() == [deletableAlarmName]} as DeleteAlarmsRequest)
  }

  void "should not delete alarms that are newer than threshold"() {
    when:
    agent.run()

    then:
    1 * autoScalingUSW.describePolicies(_) >> DescribePoliciesResponse.builder().build()
    1 * autoScalingUSE.describePolicies(_) >> DescribePoliciesResponse.builder().build()
    1 * cloudWatchUSE.describeAlarms(_) >> DescribeAlarmsResponse.builder().metricAlarms([buildAlarm(deletableAlarmName, 88)]).build()
    1 * cloudWatchUSW.describeAlarms(_) >> DescribeAlarmsResponse.builder().metricAlarms([buildAlarm(deletableAlarmName, 92)]).build()
    1 * cloudWatchUSW.deleteAlarms({ DeleteAlarmsRequest r -> r.alarmNames() == [deletableAlarmName]} as DeleteAlarmsRequest)
    0 * cloudWatchUSE.deleteAlarms(_)
  }

  void "should not delete alarms that are found in scaling policies"() {
    given:
    MetricAlarm alarmA = buildAlarm(deletableAlarmName, 99)
    MetricAlarm alarmB = buildAlarm(deletableAlarmName, 99)
    ScalingPolicy policyA = ScalingPolicy.builder().alarms([Alarm.builder().alarmName(alarmA.alarmName()).build()]).build()

    when:
    agent.run()

    then:
    1 * autoScalingUSW.describePolicies(_) >> DescribePoliciesResponse.builder().build()
    1 * autoScalingUSE.describePolicies(_) >> DescribePoliciesResponse.builder().scalingPolicies([policyA]).build()
    1 * cloudWatchUSE.describeAlarms(_) >> DescribeAlarmsResponse.builder().metricAlarms([alarmA]).build()
    1 * cloudWatchUSW.describeAlarms(_) >> DescribeAlarmsResponse.builder().metricAlarms([alarmB]).build()
    1 * cloudWatchUSW.deleteAlarms({ DeleteAlarmsRequest r -> r.alarmNames() == [deletableAlarmName]} as DeleteAlarmsRequest)
    0 * cloudWatchUSE.deleteAlarms(_)
  }

  void "should not delete alarms that do not appear to be created by Spinnaker"() {
    given:
    MetricAlarm alarmA = buildAlarm("some-other-alarm", 91)
    MetricAlarm alarmB = buildAlarm("some-other-alarm-v000-${validUuid}", 91) // missing "-alarm-"

    when:
    agent.run()

    then:
    1 * autoScalingUSE.describePolicies(_) >> DescribePoliciesResponse.builder().build()
    1 * cloudWatchUSE.describeAlarms(_) >> DescribeAlarmsResponse.builder().build()
    1 * autoScalingUSW.describePolicies(_) >> DescribePoliciesResponse.builder().build()
    1 * cloudWatchUSW.describeAlarms(_) >> DescribeAlarmsResponse.builder().metricAlarms([alarmA, alarmB]).build()
    0 * cloudWatchUSW.deleteAlarms(_)

  }

  void "should delete alarms that match a user defined pattern"() {
    agent = new CleanupAlarmsAgent(amazonClientProvider, credentialsRepository, 10L, 10L, 90, ".+-v[0-9]{3}-CustomAlarm-.+")

    given:
    MetricAlarm alarmA = buildAlarm("some-other-v000-CustomAlarm-${validUuid}", 91)
    MetricAlarm alarmB = buildAlarm("some-other-alarm-v000-${validUuid}", 91) // missing "-alarm-"

    when:
    agent.run()

    then:
    1 * autoScalingUSE.describePolicies(_) >> DescribePoliciesResponse.builder().build()
    1 * cloudWatchUSE.describeAlarms(_) >> DescribeAlarmsResponse.builder().build()
    1 * autoScalingUSW.describePolicies(_) >> DescribePoliciesResponse.builder().build()
    1 * cloudWatchUSW.describeAlarms(_) >> DescribeAlarmsResponse.builder().metricAlarms([alarmA, alarmB]).build()
    1 * cloudWatchUSW.deleteAlarms({ DeleteAlarmsRequest request ->
      request.alarmNames() == ["some-other-v000-CustomAlarm-${validUuid}"]
    })
    0 * cloudWatchUSW.deleteAlarms({ DeleteAlarmsRequest request ->
      request.alarmNames() == ["some-other-alarm-v000-${validUuid}"]
    })
  }


  void "should delete alarms that match a user defined multiple pattern"() {
    agent = new CleanupAlarmsAgent(amazonClientProvider, credentialsRepository, 10L, 10L, 90, ".+-v[0-9]{3}-CustomAlarm-.+|^some-other-alarm-v[0-9]{3}-.+")

    given:
    MetricAlarm alarmA = buildAlarm("some-other-v000-CustomAlarm-${validUuid}", 91)
    MetricAlarm alarmB = buildAlarm("some-other-alarm-v000-${validUuid}", 91) // missing "-alarm-"

    when:
    agent.run()

    then:
    1 * autoScalingUSE.describePolicies(_) >> DescribePoliciesResponse.builder().build()
    1 * cloudWatchUSE.describeAlarms(_) >> DescribeAlarmsResponse.builder().build()
    1 * autoScalingUSW.describePolicies(_) >> DescribePoliciesResponse.builder().build()
    1 * cloudWatchUSW.describeAlarms(_) >> DescribeAlarmsResponse.builder().metricAlarms([alarmA, alarmB]).build()
    1 * cloudWatchUSW.deleteAlarms({ DeleteAlarmsRequest request ->
      request.alarmNames() == ["some-other-v000-CustomAlarm-${validUuid}", "some-other-alarm-v000-${validUuid}"]
    })
  }

  private static MetricAlarm buildAlarm(String name, int dataDays) {
    MetricAlarm.builder().alarmName(name).stateUpdatedTimestamp(DateTime.now().minusDays(dataDays).toDate().toInstant()).build()
  }




}
