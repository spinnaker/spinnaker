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

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsMetricAlarm
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class EcsCloudMetricAlarmCachingAgentSpec extends Specification {
  @Shared
  String ACCOUNT = 'test-account'
  @Shared
  String REGION = 'us-west-1'
  AmazonCloudWatch cloudWatch
  AmazonClientProvider clientProvider
  ProviderCache providerCache
  AWSCredentialsProvider credentialsProvider

  @Subject
  EcsCloudMetricAlarmCachingAgent agent = new EcsCloudMetricAlarmCachingAgent(ACCOUNT, REGION, clientProvider, credentialsProvider)

  def setup() {
    cloudWatch = Mock(AmazonCloudWatch)
    clientProvider = Mock(AmazonClientProvider)
    providerCache = Mock(ProviderCache)
    credentialsProvider = Mock(AWSCredentialsProvider)
  }

  def 'should get a list of cloud watch alarms'() {
    given:
    def metricAlarm = new EcsMetricAlarm().withAlarmName("alarm-name").withAlarmArn("alarmArn")

    when:
    def alarms = agent.fetchMetricAlarms(cloudWatch)

    then:
    cloudWatch.describeAlarms(_) >> new DescribeAlarmsResult().withMetricAlarms(metricAlarm)
    alarms.contains(metricAlarm)
  }

  def 'should generate fresh data'() {
    given:
    Set metricAlarms = [new EcsMetricAlarm().withAlarmName("alarm-name-1").withAlarmArn("alarmArn-1").withAccountName(ACCOUNT).withRegion(REGION),
                        new EcsMetricAlarm().withAlarmName("alarm-name-2").withAlarmArn("alarmArn-2").withAccountName(ACCOUNT).withRegion(REGION)]
    when:
    def cacheData = agent.generateFreshData(metricAlarms)

    then:
    cacheData.size() == 1
    cacheData.get(Keys.Namespace.ALARMS.ns).size() == metricAlarms.size()
    metricAlarms*.alarmName.containsAll(cacheData.get(Keys.Namespace.ALARMS.ns)*.getAttributes().alarmName)
    metricAlarms*.alarmArn.containsAll(cacheData.get(Keys.Namespace.ALARMS.ns)*.getAttributes().alarmArn)
    metricAlarms*.accountName.containsAll(cacheData.get(Keys.Namespace.ALARMS.ns)*.getAttributes().accountName)
    metricAlarms*.region.containsAll(cacheData.get(Keys.Namespace.ALARMS.ns)*.getAttributes().region)
  }
}
