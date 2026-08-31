/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.scalingpolicy

import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.Alarm
import software.amazon.awssdk.services.autoscaling.model.DescribePoliciesRequest
import software.amazon.awssdk.services.autoscaling.model.DescribePoliciesResponse
import software.amazon.awssdk.services.autoscaling.model.PutScalingPolicyRequest
import software.amazon.awssdk.services.autoscaling.model.PutScalingPolicyResponse
import software.amazon.awssdk.services.autoscaling.model.ScalingPolicy
import software.amazon.awssdk.services.autoscaling.model.StepAdjustment
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsRequest
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse
import software.amazon.awssdk.services.cloudwatch.model.Dimension
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm
import software.amazon.awssdk.services.cloudwatch.model.PutMetricAlarmRequest
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgReferenceCopier
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.data.task.Task
import spock.lang.Specification
import spock.lang.Subject

class DefaultScalingPolicyCopierSpec extends Specification {

  def sourceAutoScaling = Mock(AutoScalingClient)
  def targetAutoScaling = Mock(AutoScalingClient)
  def sourceCredentials = Stub(NetflixAmazonCredentials) {
    getAccountId() >> 'abc'
  }
  def targetCredentials = Stub(NetflixAmazonCredentials) {
    getAccountId() >> 'def'
  }
  def sourceCloudWatch = Mock(CloudWatchClient)
  def targetCloudWatch = Mock(CloudWatchClient)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAutoScalingV2(_, 'us-east-1') >> sourceAutoScaling
    getAutoScalingV2(_, 'us-west-1') >> targetAutoScaling
    getAmazonCloudWatchV2(_, 'us-east-1') >> sourceCloudWatch
    getAmazonCloudWatchV2(_, 'us-west-1') >> targetCloudWatch
  }
  String newPolicyName = 'new_policy_name'

  def policyNameGenerator = Stub(DefaultScalingPolicyCopier.PolicyNameGenerator) {
    generateScalingPolicyName(_, _, _, _, _) >>
      'new_policy_name'
  }

  int count = 0
  def idGenerator = Stub(IdGenerator) {
    nextId() >> { (++count).toString() }
  }

  void cleanup() {
    count = 0
  }

  @Subject
  ScalingPolicyCopier scalingPolicyCopier = new DefaultScalingPolicyCopier(amazonClientProvider, idGenerator, policyNameGenerator)

  void 'should copy nothing when there are no scaling policies'() {
    when:
    scalingPolicyCopier.copyScalingPolicies(Mock(Task), 'asgard-v000', 'asgard-v001', sourceCredentials, targetCredentials, 'us-east-1', 'us-west-1')

    then:
    1 * sourceAutoScaling.describePolicies(DescribePoliciesRequest.builder().autoScalingGroupName('asgard-v000').build()) >>
      DescribePoliciesResponse.builder().scalingPolicies([]).build()
    0 * targetAutoScaling.putScalingPolicy(_)
    0 * sourceCloudWatch.describeAlarms(_)
    0 * targetCloudWatch.putMetricAlarm(_)
  }

  void 'should omit actions that are specific to the source account/region when they differ'() {
    given:
    def replacements = ['oldPolicyARN': 'newPolicyARN']
    def actions = ['oldPolicyARN', 'sns:us-east-1', "sns:${sourceCredentials.accountId}:someQueue", 'ok-one']

    when:
    def replacedActions = scalingPolicyCopier.replacePolicyArnActions('us-east-1', 'us-west-1', sourceCredentials, targetCredentials, replacements, actions)

    then:
    replacedActions == ['ok-one', 'newPolicyARN']
  }

  void 'generates a semantically meaningful alarm name'() {
    given:
    ScalingPolicy policy = ScalingPolicy.builder().policyARN('oldPolicyARN1').autoScalingGroupName('asgard-v000').policyName('policy1').scalingAdjustment(5).adjustmentType('ChangeInCapacity').cooldown(100).minAdjustmentStep(2).alarms([Alarm.builder().alarmName('alarm1').build()]).build()
    MetricAlarm alarm = MetricAlarm.builder().alarmName('alarm1').alarmDescription('alarm 1 description').actionsEnabled(true).okActions([]).alarmActions(['oldPolicyARN1']).insufficientDataActions([]).metricName('metric1').namespace('namespace1').statistic('statistic1').dimensions([
          Dimension.builder().name(AsgReferenceCopier.DIMENSION_NAME_FOR_ASG).value('asgard-v000').build()
        ]).period(1).unit('unit1').evaluationPeriods(2).threshold(4.2).comparisonOperator('GreaterThanOrEqualToThreshold').build()

    DefaultScalingPolicyCopier.PolicyNameGenerator generator = new DefaultScalingPolicyCopier.PolicyNameGenerator(idGenerator, amazonClientProvider)

    when:
    String result = generator.generateScalingPolicyName(sourceCredentials, 'us-east-1', 'asgard-v010', 'asgard-v011', policy)

    then:
    result.startsWith('asgard-v011-namespace1-metric1-GreaterThanOrEqualToThreshold-4.2-2-1-')
    1 * sourceCloudWatch.describeAlarms(DescribeAlarmsRequest.builder().alarmNames(['alarm1']).build()) >> DescribeAlarmsResponse.builder().metricAlarms([
      alarm
    ]).build()
  }

  void 'generates a valid Scaling Policy Name'() {
    given:
    ScalingPolicy policy = ScalingPolicy.builder().policyARN('oldPolicyARN1').autoScalingGroupName('asgard-v000').policyName('policy1').scalingAdjustment(5).adjustmentType('ChangeInCapacity').cooldown(100).minAdjustmentStep(2).alarms([Alarm.builder().alarmName('alarm1').build()]).build()
    MetricAlarm alarm = MetricAlarm.builder().alarmName('alarm1').alarmDescription('alarm 1 description').actionsEnabled(true).okActions([]).alarmActions(['oldPolicyARN1']).insufficientDataActions([]).metricName('Metric1.with-all_acceptable/special#chars:defined').namespace('Namespace1.with-all_acceptable/special#chars:defined').statistic('statistic1').dimensions([
          Dimension.builder().name(AsgReferenceCopier.DIMENSION_NAME_FOR_ASG).value('asgard-v000').build()
        ]).period(1).unit('unit1').evaluationPeriods(2).threshold(4.2).comparisonOperator('GreaterThanOrEqualToThreshold').build()

    DefaultScalingPolicyCopier.PolicyNameGenerator generator = new DefaultScalingPolicyCopier.PolicyNameGenerator(idGenerator, amazonClientProvider)

    when:
    String result = generator.generateScalingPolicyName(sourceCredentials, 'us-east-1', 'asgard-v010', 'asgard-v011', policy)

    then:
    result.startsWith('asgard-v011-Namespace1.with-all_acceptable/special#chars-defined-Metric1.with-all_acceptable/special#chars-defined-GreaterThanOrEqualToThreshold-4.2-2-1-')
    1 * sourceCloudWatch.describeAlarms(DescribeAlarmsRequest.builder().alarmNames(['alarm1']).build()) >> DescribeAlarmsResponse.builder().metricAlarms([
      alarm
    ]).build()
  }

  void 'falls back to asg name replacement when no alarms found'() {
    given:
    ScalingPolicy policy = ScalingPolicy.builder().policyARN('oldPolicyARN1').autoScalingGroupName('asgard-v000').policyName('asgard-v010-blah-blah-blah').scalingAdjustment(5).adjustmentType('ChangeInCapacity').cooldown(100).minAdjustmentStep(2).alarms([]).build()
    DefaultScalingPolicyCopier.PolicyNameGenerator generator = new DefaultScalingPolicyCopier.PolicyNameGenerator(idGenerator, amazonClientProvider)

    when:
    String result = generator.generateScalingPolicyName(sourceCredentials, 'us-east-1', 'asgard-v010', 'asgard-v011', policy)

    then:
    result == 'asgard-v011-blah-blah-blah'
  }

  void 'should copy scaling policies and alarms'() {
    when:
    scalingPolicyCopier.copyScalingPolicies(Mock(Task), 'asgard-v000', 'asgard-v001', sourceCredentials, targetCredentials, 'us-east-1', 'us-west-1')

    then:
    1 * sourceAutoScaling.describePolicies(DescribePoliciesRequest.builder().autoScalingGroupName('asgard-v000').build()) >>
      DescribePoliciesResponse.builder().scalingPolicies([
        ScalingPolicy.builder().policyARN('oldPolicyARN1').autoScalingGroupName('asgard-v000').policyName('policy1').scalingAdjustment(5).adjustmentType('ChangeInCapacity').cooldown(100).minAdjustmentStep(2).alarms(['alarm1', 'alarm2'].collect { Alarm.builder().alarmName(it).build() }).build(),
        ScalingPolicy.builder().policyARN('oldPolicyARN2').autoScalingGroupName('asgard-v000').policyName('policy2').scalingAdjustment(10).adjustmentType('PercentChangeInCapacity').cooldown(200).minAdjustmentStep(3).minAdjustmentMagnitude(20).metricAggregationType("Average").estimatedInstanceWarmup(30).stepAdjustments([
            StepAdjustment.builder().metricIntervalLowerBound(10.5).metricIntervalUpperBound(11.5).scalingAdjustment(90).build()
          ]).policyType("StepScaling").alarms(['alarm2', 'alarm3'].collect { Alarm.builder().alarmName(it).build() }).build()
      ]).build()
    1 * targetAutoScaling.putScalingPolicy(PutScalingPolicyRequest.builder().autoScalingGroupName('asgard-v001').policyName(newPolicyName).scalingAdjustment(5).adjustmentType('ChangeInCapacity').cooldown(100).minAdjustmentStep(2).build()) >> PutScalingPolicyResponse.builder().policyARN('newPolicyARN1').build()
    1 * targetAutoScaling.putScalingPolicy(PutScalingPolicyRequest.builder().autoScalingGroupName('asgard-v001').policyName(newPolicyName).scalingAdjustment(10).adjustmentType('PercentChangeInCapacity').cooldown(200).minAdjustmentStep(3).minAdjustmentMagnitude(20).metricAggregationType("Average").estimatedInstanceWarmup(30).stepAdjustments([
        StepAdjustment.builder().metricIntervalLowerBound(10.5).metricIntervalUpperBound(11.5).scalingAdjustment(90).build()
      ]).policyType("StepScaling").build()) >> PutScalingPolicyResponse.builder().policyARN('newPolicyARN2').build()

    1 * sourceCloudWatch.describeAlarms(DescribeAlarmsRequest.builder().alarmNames(['alarm1', 'alarm2', 'alarm3']).build()) >> DescribeAlarmsResponse.builder().metricAlarms([
      MetricAlarm.builder().alarmName('alarm1').alarmDescription('alarm 1 description').actionsEnabled(true).okActions([]).alarmActions(['oldPolicyARN1']).insufficientDataActions([]).metricName('metric1').namespace('namespace1').statistic('statistic1').dimensions([
          Dimension.builder().name(AsgReferenceCopier.DIMENSION_NAME_FOR_ASG).value('asgard-v000').build()
        ]).period(1).unit('unit1').evaluationPeriods(2).threshold(4.2).comparisonOperator('GreaterThanOrEqualToThreshold').build(),
      MetricAlarm.builder().alarmName('alarm2').alarmDescription('alarm 2 description').actionsEnabled(true).okActions([]).alarmActions(['oldPolicyARN1', 'oldPolicyARN2', 'action1']).insufficientDataActions([]).metricName('metric2').namespace('namespace2').statistic('statistic2').dimensions([
          Dimension.builder().name(AsgReferenceCopier.DIMENSION_NAME_FOR_ASG).value('asgard-v000').build(),
          Dimension.builder().name('other').value('dimension1').build()
        ]).period(10).unit('unit2').evaluationPeriods(20).threshold(40.2).comparisonOperator('LessThanOrEqualToThreshold').build(),
      MetricAlarm.builder().alarmName('alarm3').alarmDescription('alarm 3 description').actionsEnabled(false).okActions([]).alarmActions([]).insufficientDataActions(['oldPolicyARN2']).metricName('metric3').namespace('namespace3').statistic('statistic3').dimensions([]).period(31).unit('unit3').evaluationPeriods(32).threshold(34).comparisonOperator('GreaterThanThreshold').build(),
    ]).build()
    1 * targetCloudWatch.putMetricAlarm(PutMetricAlarmRequest.builder().alarmName('asgard-v001-alarm-1').alarmDescription('alarm 1 description').actionsEnabled(true).okActions([]).alarmActions(['newPolicyARN1']).insufficientDataActions([]).metricName('metric1').namespace('namespace1').statistic('statistic1').dimensions([
        Dimension.builder().name(AsgReferenceCopier.DIMENSION_NAME_FOR_ASG).value('asgard-v001').build()
      ]).period(1).unit('unit1').evaluationPeriods(2).threshold(4.2).comparisonOperator('GreaterThanOrEqualToThreshold').build())
    1 * targetCloudWatch.putMetricAlarm(PutMetricAlarmRequest.builder().alarmName('asgard-v001-alarm-2').alarmDescription('alarm 2 description').actionsEnabled(true).okActions([]).alarmActions(['action1', 'newPolicyARN1', 'newPolicyARN2']).insufficientDataActions([]).metricName('metric2').namespace('namespace2').statistic('statistic2').dimensions([
        Dimension.builder().name('other').value('dimension1').build(),
        Dimension.builder().name(AsgReferenceCopier.DIMENSION_NAME_FOR_ASG).value('asgard-v001').build()
      ]).period(10).unit('unit2').evaluationPeriods(20).threshold(40.2).comparisonOperator('LessThanOrEqualToThreshold').build())
    1 * targetCloudWatch.putMetricAlarm(PutMetricAlarmRequest.builder().alarmName('asgard-v001-alarm-3').alarmDescription('alarm 3 description').actionsEnabled(false).okActions([]).alarmActions([]).insufficientDataActions(['newPolicyARN2']).metricName('metric3').namespace('namespace3').statistic('statistic3').dimensions([]).period(31).unit('unit3').evaluationPeriods(32).threshold(34).comparisonOperator('GreaterThanThreshold').build())
  }
}
