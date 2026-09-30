/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent

import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsRequest
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse
import software.amazon.awssdk.services.cloudwatch.model.Dimension
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class EcsCloudMetricAlarmCachingAgentSpec extends Specification {
  @Shared
  String ACCOUNT = 'test-account'
  @Shared
  String REGION = 'us-west-1'
  CloudWatchClient cloudWatch
  AmazonClientProvider clientProvider
  ProviderCache providerCache

  @Subject
  EcsCloudMetricAlarmCachingAgent agent

  def setup() {
    cloudWatch = Mock(CloudWatchClient)
    clientProvider = Mock(AmazonClientProvider)
    providerCache = Mock(ProviderCache)
    agent = new EcsCloudMetricAlarmCachingAgent(CommonCachingAgent.netflixAmazonCredentials, 'us-west-1', clientProvider)
  }

  def 'should get a list of cloud watch alarms'() {
    given:
    def metricAlarm = MetricAlarm.builder().alarmName("alarm-name").alarmArn("alarmArn").build()

    when:
    def alarms = agent.fetchMetricAlarms(cloudWatch)

    then:
    cloudWatch.describeAlarms(_ as DescribeAlarmsRequest) >> DescribeAlarmsResponse.builder().metricAlarms([metricAlarm]).build()
    alarms.contains(metricAlarm)
  }

  def 'should generate fresh data'() {
    given:
    Set metricAlarms = [
      MetricAlarm.builder().alarmName("alarm-name-1").alarmArn("alarmArn-1").build(),
      MetricAlarm.builder().alarmName("alarm-name-2").alarmArn("alarmArn-2").build()
    ]
    when:
    def cacheData = agent.generateFreshData(metricAlarms)

    then:
    cacheData.size() == 1
    cacheData.get(Keys.Namespace.ALARMS.ns).size() == metricAlarms.size()
  }

  def 'loadData caches alarms with the expected keys and attributes'() {
    given:
    def metricAlarm1 = MetricAlarm.builder().alarmName("alarm-name-1").alarmArn("alarmArn-1")
      .dimensions([Dimension.builder().name("ClusterName").value("my-cluster").build()])
      .build()
    def metricAlarm2 = MetricAlarm.builder().alarmName("alarm-name-2").alarmArn("alarmArn-2")
      .dimensions([Dimension.builder().name("ClusterName").value("my-cluster").build()])
      .build()
    def attributes1 = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(metricAlarm1, ACCOUNT, REGION)
    def attributes2 = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(metricAlarm2, ACCOUNT, REGION)
    def metricAlarms = [metricAlarm1, metricAlarm2]
    cloudWatch.describeAlarms(_ as DescribeAlarmsRequest) >> DescribeAlarmsResponse.builder().metricAlarms(metricAlarms).build()
    clientProvider.getAmazonCloudWatchV2(_, _) >> cloudWatch

    def newKey1 = Keys.getAlarmKey(ACCOUNT, REGION, metricAlarm1.alarmArn(), "my-cluster")
    def newKey2 = Keys.getAlarmKey(ACCOUNT, REGION, metricAlarm2.alarmArn(), "my-cluster")

    when:
    def cacheResult = agent.loadData(providerCache)

    then:
    cacheResult.cacheResults[Keys.Namespace.ALARMS.ns].size() == 2
    cacheResult.cacheResults[Keys.Namespace.ALARMS.ns]*.id.containsAll([newKey1, newKey2])
    cacheResult.cacheResults[Keys.Namespace.ALARMS.ns]*.attributes.containsAll([attributes1, attributes2])
  }

  def 'should still report the alarms namespace with an empty list when there are no live alarms'() {
    given:
    clientProvider.getAmazonCloudWatchV2(_, _) >> cloudWatch
    cloudWatch.describeAlarms(_ as DescribeAlarmsRequest) >> DescribeAlarmsResponse.builder().metricAlarms([]).build()

    when:
    def result = agent.loadData(providerCache)

    then:
    result.cacheResults.containsKey(Keys.Namespace.ALARMS.ns)
    result.cacheResults[Keys.Namespace.ALARMS.ns].isEmpty()
  }

}
