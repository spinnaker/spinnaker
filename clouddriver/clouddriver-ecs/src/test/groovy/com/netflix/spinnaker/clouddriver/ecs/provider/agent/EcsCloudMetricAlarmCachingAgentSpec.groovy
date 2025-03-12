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
import com.amazonaws.services.cloudwatch.model.Dimension
import com.amazonaws.services.cloudwatch.model.MetricAlarm
import com.netflix.spinnaker.cats.cache.DefaultCacheData
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
  EcsCloudMetricAlarmCachingAgent agent

  def setup() {
    cloudWatch = Mock(AmazonCloudWatch)
    clientProvider = Mock(AmazonClientProvider)
    providerCache = Mock(ProviderCache)
    credentialsProvider = Mock(AWSCredentialsProvider)
    agent = new EcsCloudMetricAlarmCachingAgent(CommonCachingAgent.netflixAmazonCredentials, 'us-west-1', clientProvider)

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

  def 'should evict old keys when id is appended'() {
    given:
    def metricAlarm1 = new MetricAlarm().withAlarmName("alarm-name-1").withAlarmArn("alarmArn-1").withDimensions([new Dimension().withName("ClusterName").withValue("my-cluster")])
    def metricAlarm2 = new MetricAlarm().withAlarmName("alarm-name-2").withAlarmArn("alarmArn-2").withDimensions([new Dimension().withName("ClusterName").withValue("my-cluster")])
    def attributes1 = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(metricAlarm1, ACCOUNT, REGION)
    def attributes2 = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(metricAlarm2, ACCOUNT, REGION)
    def metricAlarms = [metricAlarm1, metricAlarm2]
    def describeAlarmsResult = new DescribeAlarmsResult().withMetricAlarms(metricAlarms)
    cloudWatch.describeAlarms(_) >> describeAlarmsResult
    clientProvider.getAmazonCloudWatch(_, _, _) >> cloudWatch

    def oldKey1 = Keys.buildKey(Keys.Namespace.ALARMS.ns, ACCOUNT, REGION, metricAlarm1.getAlarmArn())
    def oldKey2 = Keys.buildKey(Keys.Namespace.ALARMS.ns, ACCOUNT, REGION, metricAlarm2.getAlarmArn())
    def oldData = [new DefaultCacheData(oldKey1, attributes1, [:]), new DefaultCacheData(oldKey2, attributes2, [:])]
    providerCache.getAll(Keys.Namespace.ALARMS.ns) >> oldData

    def newKey1 = Keys.getAlarmKey(ACCOUNT, REGION, metricAlarm1.getAlarmArn(), "my-cluster")
    def newKey2 = Keys.getAlarmKey(ACCOUNT, REGION, metricAlarm2.getAlarmArn(), "my-cluster")

    when:
    def cacheResult = agent.loadData(providerCache)

    then:
    cacheResult.evictions[Keys.Namespace.ALARMS.ns].size() == 2
    cacheResult.evictions[Keys.Namespace.ALARMS.ns].containsAll([oldKey1, oldKey2])
    cacheResult.cacheResults[Keys.Namespace.ALARMS.ns].size() == 2
    cacheResult.cacheResults[Keys.Namespace.ALARMS.ns]*.id.containsAll([newKey1, newKey2])
    cacheResult.cacheResults[Keys.Namespace.ALARMS.ns]*.attributes.containsAll([attributes1, attributes2])
  }

}
