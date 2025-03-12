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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.DescribePoliciesResult
import com.amazonaws.services.autoscaling.model.ScalingPolicy
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.DeleteAlarmsRequest
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult
import com.amazonaws.services.cloudwatch.model.MetricAlarm
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.credentials.CredentialsRepository
import org.joda.time.DateTime
import spock.lang.Shared
import spock.lang.Specification

class CleanupAlarmsAgentSpec extends Specification {

  @Shared
  def test = TestCredential.named('test')

  AmazonAutoScaling autoScalingUSW
  AmazonAutoScaling autoScalingUSE
  AmazonCloudWatch cloudWatchUSW
  AmazonCloudWatch cloudWatchUSE
  AmazonClientProvider amazonClientProvider
  CredentialsRepository credentialsRepository
  CleanupAlarmsAgent agent
  String validUuid = UUID.randomUUID().toString()
  String deletableAlarmName = "clouddriver-test-v123-alarm-" + validUuid

  void setup() {
    autoScalingUSW = Mock(AmazonAutoScaling)
    autoScalingUSE = Mock(AmazonAutoScaling)
    cloudWatchUSW = Mock(AmazonCloudWatch)
    cloudWatchUSE = Mock(AmazonCloudWatch)

    amazonClientProvider = Mock(AmazonClientProvider) {
      1 * getAutoScaling(test, "us-west-1") >> autoScalingUSW
      1 * getAutoScaling(test, "us-east-1") >> autoScalingUSE
      1 * getCloudWatch(test, "us-west-1") >> cloudWatchUSW
      1 * getCloudWatch(test, "us-east-1") >> cloudWatchUSE
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
    1 * autoScalingUSW.describePolicies() >> new DescribePoliciesResult()
    1 * autoScalingUSE.describePolicies() >> new DescribePoliciesResult()
    1 * cloudWatchUSE.describeAlarms(_) >> new DescribeAlarmsResult().withMetricAlarms([buildAlarm(deletableAlarmName, 92)])
    1 * cloudWatchUSW.describeAlarms(_) >> new DescribeAlarmsResult().withMetricAlarms([buildAlarm(deletableAlarmName, 92)])
    1 * cloudWatchUSE.deleteAlarms({ DeleteAlarmsRequest r -> r.alarmNames == [deletableAlarmName]} as DeleteAlarmsRequest)
    1 * cloudWatchUSW.deleteAlarms({ DeleteAlarmsRequest r -> r.alarmNames == [deletableAlarmName]} as DeleteAlarmsRequest)
  }

  void "should not delete alarms that are newer than threshold"() {
    when:
    agent.run()

    then:
    1 * autoScalingUSW.describePolicies() >> new DescribePoliciesResult()
    1 * autoScalingUSE.describePolicies() >> new DescribePoliciesResult()
    1 * cloudWatchUSE.describeAlarms(_) >> new DescribeAlarmsResult().withMetricAlarms([buildAlarm(deletableAlarmName, 88)])
    1 * cloudWatchUSW.describeAlarms(_) >> new DescribeAlarmsResult().withMetricAlarms([buildAlarm(deletableAlarmName, 92)])
    1 * cloudWatchUSW.deleteAlarms({ DeleteAlarmsRequest r -> r.alarmNames == [deletableAlarmName]} as DeleteAlarmsRequest)
    0 * cloudWatchUSE.deleteAlarms(_)
  }

  void "should not delete alarms that are found in scaling policies"() {
    given:
    MetricAlarm alarmA = buildAlarm(deletableAlarmName, 99)
    MetricAlarm alarmB = buildAlarm(deletableAlarmName, 99)
    ScalingPolicy policyA = new ScalingPolicy(alarms: [alarmA])

    when:
    agent.run()

    then:
    1 * autoScalingUSW.describePolicies() >> new DescribePoliciesResult()
    1 * autoScalingUSE.describePolicies() >> new DescribePoliciesResult().withScalingPolicies([policyA])
    1 * cloudWatchUSE.describeAlarms(_) >> new DescribeAlarmsResult().withMetricAlarms([alarmA])
    1 * cloudWatchUSW.describeAlarms(_) >> new DescribeAlarmsResult().withMetricAlarms([alarmB])
    1 * cloudWatchUSW.deleteAlarms({ DeleteAlarmsRequest r -> r.alarmNames == [deletableAlarmName]} as DeleteAlarmsRequest)
    0 * cloudWatchUSE.deleteAlarms(_)
  }

  void "should not delete alarms that do not appear to be created by Spinnaker"() {
    given:
    MetricAlarm alarmA = buildAlarm("some-other-alarm", 91)
    MetricAlarm alarmB = buildAlarm("some-other-alarm-v000-${validUuid}", 91) // missing "-alarm-"

    when:
    agent.run()

    then:
    1 * autoScalingUSE.describePolicies() >> new DescribePoliciesResult()
    1 * cloudWatchUSE.describeAlarms(_) >> new DescribeAlarmsResult()
    1 * autoScalingUSW.describePolicies() >> new DescribePoliciesResult()
    1 * cloudWatchUSW.describeAlarms(_) >> new DescribeAlarmsResult().withMetricAlarms([alarmA, alarmB])
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
    1 * autoScalingUSE.describePolicies() >> new DescribePoliciesResult()
    1 * cloudWatchUSE.describeAlarms(_) >> new DescribeAlarmsResult()
    1 * autoScalingUSW.describePolicies() >> new DescribePoliciesResult()
    1 * cloudWatchUSW.describeAlarms(_) >> new DescribeAlarmsResult().withMetricAlarms([alarmA, alarmB])
    1 * cloudWatchUSW.deleteAlarms({ DeleteAlarmsRequest request ->
      request.alarmNames == ["some-other-v000-CustomAlarm-${validUuid}"]
    })
    0 * cloudWatchUSW.deleteAlarms({ DeleteAlarmsRequest request ->
      request.alarmNames == ["some-other-alarm-v000-${validUuid}"]
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
    1 * autoScalingUSE.describePolicies() >> new DescribePoliciesResult()
    1 * cloudWatchUSE.describeAlarms(_) >> new DescribeAlarmsResult()
    1 * autoScalingUSW.describePolicies() >> new DescribePoliciesResult()
    1 * cloudWatchUSW.describeAlarms(_) >> new DescribeAlarmsResult().withMetricAlarms([alarmA, alarmB])
    1 * cloudWatchUSW.deleteAlarms({ DeleteAlarmsRequest request ->
      request.alarmNames == ["some-other-v000-CustomAlarm-${validUuid}", "some-other-alarm-v000-${validUuid}"]
    })
  }

  private static MetricAlarm buildAlarm(String name, int dataDays) {
    new MetricAlarm(alarmName: name, stateUpdatedTimestamp: DateTime.now().minusDays(dataDays).toDate())
  }




}
