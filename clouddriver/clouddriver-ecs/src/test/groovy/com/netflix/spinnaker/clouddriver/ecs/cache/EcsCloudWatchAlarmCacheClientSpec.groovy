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

package com.netflix.spinnaker.clouddriver.ecs.cache

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsCloudWatchAlarmCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsMetricAlarm
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.CommonCachingAgent
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.EcsCloudMetricAlarmCachingAgent
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.Dimension
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import java.util.stream.Collectors
import java.util.stream.Stream

class EcsCloudWatchAlarmCacheClientSpec extends Specification {
  @Subject
  EcsCloudWatchAlarmCacheClient client

  @Subject
  EcsCloudMetricAlarmCachingAgent agent

  @Shared
  String ACCOUNT = 'test-account'

  @Shared
  String REGION = 'us-west-1'

  Cache cacheView
  CloudWatchClient cloudWatch
  AmazonClientProvider clientProvider
  ProviderCache providerCache

  def setup() {
    cacheView = Mock(Cache)
    client = new EcsCloudWatchAlarmCacheClient(cacheView)
    cloudWatch = Mock(CloudWatchClient)
    clientProvider = Mock(AmazonClientProvider)
    providerCache = Mock(ProviderCache)
    agent = new EcsCloudMetricAlarmCachingAgent(CommonCachingAgent.netflixAmazonCredentials, REGION, clientProvider)
  }

  def 'should convert cache data into object'() {
    given:
    def ecsClusterName = 'my-cluster'
    def metricAlarm = new EcsMetricAlarm().withAlarmName("alarm-name").withAlarmArn("alarmArn").withRegion(REGION).withAccountName(ACCOUNT)
    def key = Keys.getAlarmKey(ACCOUNT, REGION, metricAlarm.getAlarmArn(), ecsClusterName)
    def v2Alarm = MetricAlarm.builder().alarmName("alarm-name").alarmArn("alarmArn").build()
    def attributes = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(v2Alarm, ACCOUNT, REGION)

    when:
    def returnedMetricAlarm = client.get(key)

    then:
    cacheView.get(Keys.Namespace.ALARMS.ns, key) >> new DefaultCacheData(key, attributes, [:])
    returnedMetricAlarm == metricAlarm
  }

  def 'should return metric alarms for a service - single cluster'() {
    given:
    def serviceName = 'my-service'
    def serviceName2 = 'not-matching-service'
    def ecsClusterName = 'my-cluster'

    def alarm1 = MetricAlarm.builder().alarmName("alarm-name").alarmArn("alarmArn")
      .alarmActions("arn:aws:sns:us-west-1:123456789012:${serviceName}")
      .dimensions([Dimension.builder().name("ClusterName").value(ecsClusterName).build()])
      .build()
    def alarm2 = MetricAlarm.builder().alarmName("alarm-name-2").alarmArn("alarmArn2")
      .alarmActions("arn:aws:sns:us-west-1:123456789012:${serviceName}")
      .dimensions([Dimension.builder().name("ClusterName").value(ecsClusterName).build()])
      .build()
    def alarm3 = MetricAlarm.builder().alarmName("alarm-name").alarmArn("alarmArn3")
      .alarmActions("arn:aws:sns:us-west-1:123456789012:${serviceName2}")
      .dimensions([Dimension.builder().name("ClusterName").value(ecsClusterName).build()])
      .build()

    def metricAlarms = [alarm1, alarm2, alarm3]
    def keys = metricAlarms.collect { alarm ->
      def key = Keys.getAlarmKey(ACCOUNT, REGION, alarm.alarmArn(), ecsClusterName)
      def attributes = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(alarm, ACCOUNT, REGION)
      [key, new DefaultCacheData(key, attributes, [:])]
    }

    cacheView.filterIdentifiers(Keys.Namespace.ALARMS.ns, _) >> keys*.first()
    cacheView.getAll(Keys.Namespace.ALARMS.ns, _) >> keys*.last()

    when:
    def metricAlarmsReturned = client.getMetricAlarms(serviceName, ACCOUNT, REGION, ecsClusterName)

    then:
    metricAlarmsReturned.size() == 2
    metricAlarmsReturned*.alarmName.containsAll(["alarm-name", "alarm-name-2"])
    metricAlarmsReturned*.alarmArn.containsAll(["alarmArn", "alarmArn2"])
    !metricAlarmsReturned*.alarmArn.contains(["alarmArn3"])
  }

  def 'should return metric alarms for a service - multiple clusters'() {
    given:
    def serviceName = 'my-service'
    def ecsClusterName = 'my-cluster'
    def ecsClusterName2 = 'my-cluster-2'

    def alarm1 = MetricAlarm.builder().alarmName("alarm-name").alarmArn("alarmArn")
      .alarmActions("arn:aws:sns:us-west-1:123456789012:${serviceName}")
      .dimensions([Dimension.builder().name("ClusterName").value(ecsClusterName).build()])
      .build()
    def alarm2 = MetricAlarm.builder().alarmName("alarm-name-2").alarmArn("alarmArn2")
      .alarmActions("arn:aws:sns:us-west-1:123456789012:${serviceName}")
      .dimensions([Dimension.builder().name("ClusterName").value(ecsClusterName).build()])
      .build()
    def alarm3 = MetricAlarm.builder().alarmName("alarm-name3").alarmArn("alarmArn3")
      .alarmActions("arn:aws:sns:us-west-1:123456789012:${serviceName}")
      .dimensions([Dimension.builder().name("ClusterName").value(ecsClusterName2).build()])
      .build()

    def key1 = Keys.getAlarmKey(ACCOUNT, REGION, alarm1.alarmArn(), ecsClusterName)
    def attributes1 = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(alarm1, ACCOUNT, REGION)
    def key2 = Keys.getAlarmKey(ACCOUNT, REGION, alarm2.alarmArn(), ecsClusterName)
    def attributes2 = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(alarm2, ACCOUNT, REGION)
    def key3 = Keys.getAlarmKey(ACCOUNT, REGION, alarm3.alarmArn(), ecsClusterName2)
    def attributes3 = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(alarm3, ACCOUNT, REGION)

    cacheView.filterIdentifiers(Keys.Namespace.ALARMS.ns, Keys.getAlarmKey(ACCOUNT, REGION, "*", ecsClusterName)) >> [key1, key2]
    cacheView.filterIdentifiers(Keys.Namespace.ALARMS.ns, Keys.getAlarmKey(ACCOUNT, REGION, "*", ecsClusterName2)) >> [key3]
    cacheView.getAll(Keys.Namespace.ALARMS.ns, [key1, key2]) >> [
      new DefaultCacheData(key1, attributes1, [:]),
      new DefaultCacheData(key2, attributes2, [:])
    ]
    cacheView.getAll(Keys.Namespace.ALARMS.ns, [key3]) >> [
      new DefaultCacheData(key3, attributes3, [:])
    ]

    when:
    def metricAlarmsReturned = client.getMetricAlarms(serviceName, ACCOUNT, REGION, ecsClusterName)
    def metricAlarmsReturned2 = client.getMetricAlarms(serviceName, ACCOUNT, REGION, ecsClusterName2)

    then:
    metricAlarmsReturned.size() == 2
    metricAlarmsReturned*.alarmName.containsAll(["alarm-name", "alarm-name-2"])
    metricAlarmsReturned2.size() == 1
    metricAlarmsReturned2*.alarmName.containsAll(["alarm-name3"])
  }

  def 'should return empty list if no metric alarms match the service'() {
    given:
    def serviceName = 'my-service'
    def ecsClusterName = 'my-cluster'

    def alarm1 = MetricAlarm.builder().alarmName("alarm-name").alarmArn("alarmArn")
      .alarmActions("arn:aws:sns:us-west-1:123456789012:${serviceName}")
      .dimensions([Dimension.builder().name("ClusterName").value(ecsClusterName).build()])
      .build()
    def key = Keys.getAlarmKey(ACCOUNT, REGION, alarm1.alarmArn(), ecsClusterName)
    def attributes = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(alarm1, ACCOUNT, REGION)

    cacheView.filterIdentifiers(Keys.Namespace.ALARMS.ns, _) >> [key]
    cacheView.getAll(Keys.Namespace.ALARMS.ns, _) >> [new DefaultCacheData(key, attributes, [:])]

    when:
    def metricAlarmsReturned = client.getMetricAlarms("some-other-service", ACCOUNT, REGION, ecsClusterName)

    then:
    metricAlarmsReturned.isEmpty()
  }

  def 'should return metric alarms with actions matching the service'() {
    given:
    def serviceName = 'my-service'
    def ecsClusterName = 'my-cluster'

    def alarm1 = MetricAlarm.builder().alarmName("alarm-name").alarmArn("alarmArn")
      .alarmActions("arn:aws:sns:us-west-1:123456789012:${serviceName}-AlarmsActions")
      .okActions("arn:aws:sns:us-west-1:123456789012:${serviceName}-OKActions")
      .insufficientDataActions("arn:aws:sns:us-west-1:123456789012:${serviceName}-InsufficientDataActions")
      .dimensions([Dimension.builder().name("ClusterName").value(ecsClusterName).build()])
      .build()
    def alarm2 = MetricAlarm.builder().alarmName("alarm-name-2").alarmArn("alarmArn2")
      .dimensions([Dimension.builder().name("ClusterName").value(ecsClusterName).build()])
      .build()
    def alarm3 = MetricAlarm.builder().alarmName("alarm-name-3").alarmArn("alarmArn3")
      .alarmActions(
        "arn:aws:sns:us-west-1:123456789012:${serviceName}-AlarmsActions1",
        "arn:aws:sns:us-west-1:123456789012:${serviceName}-AlarmsActions2",
        "arn:aws:sns:us-west-1:123456789012:${serviceName}-AlarmsActions3"
      )
      .okActions("arn:aws:sns:us-west-1:123456789012:${serviceName}-OKActions1")
      .insufficientDataActions("arn:aws:sns:us-west-1:123456789012:${serviceName}-InsufficientDataActions1")
      .dimensions([Dimension.builder().name("ClusterName").value(ecsClusterName).build()])
      .build()

    def metricAlarms = [alarm1, alarm2, alarm3]
    def keys = metricAlarms.collect { alarm ->
      def key = Keys.getAlarmKey(ACCOUNT, REGION, alarm.alarmArn(), ecsClusterName)
      def attributes = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(alarm, ACCOUNT, REGION)
      [key, new DefaultCacheData(key, attributes, [:])]
    }

    cacheView.filterIdentifiers(Keys.Namespace.ALARMS.ns, _) >> keys*.first()
    cacheView.getAll(Keys.Namespace.ALARMS.ns, _) >> keys*.last()

    when:
    def metricAlarmsReturned = client.getMetricAlarms(serviceName, ACCOUNT, REGION, ecsClusterName)

    then:
    metricAlarmsReturned.size() == 2
    metricAlarmsReturned*.alarmName.containsAll(["alarm-name", "alarm-name-3"])
    metricAlarmsReturned*.alarmArn.containsAll(["alarmArn", "alarmArn3"])
  }

  def 'should return metric alarms for a service - single cluster with Custom Alarms/Cloudwatch Dimensions'() {
    given:
    def serviceName = 'my-service'
    def serviceName2 = 'not-matching-service'
    def ecsClusterName = 'my-cluster'

    def alarm1 = MetricAlarm.builder().alarmName("alarm-name").alarmArn("alarmArn")
      .alarmActions("arn:aws:sns:us-west-1:123456789012:${serviceName}")
      .dimensions([Dimension.builder().name("ClusterName").value(ecsClusterName).build()])
      .build()
    def alarm2 = MetricAlarm.builder().alarmName("alarm-name-2").alarmArn("alarmArn2")
      .alarmActions("arn:aws:sns:us-west-1:123456789012:${serviceName}")
      .dimensions([Dimension.builder().name("ClusterName").value(ecsClusterName).build()])
      .build()
    def alarm3 = MetricAlarm.builder().alarmName("alarm-name").alarmArn("alarmArn3")
      .alarmActions("arn:aws:sns:us-west-1:123456789012:${serviceName2}")
      .dimensions([Dimension.builder().name("ClusterName").value(ecsClusterName).build()])
      .build()
    def alarmCustom = MetricAlarm.builder().alarmName("alarm-name-2-custom").alarmArn("alarmArn2-custom")
      .alarmActions("arn:aws:sns:us-west-1:123456789012:${serviceName}")
      .dimensions([Dimension.builder().name("CustomDimension").value("customValue").build()])
      .build()

    def keys = [alarm1, alarm2, alarm3].collect { alarm ->
      def key = Keys.getAlarmKey(ACCOUNT, REGION, alarm.alarmArn(), ecsClusterName)
      def attributes = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(alarm, ACCOUNT, REGION)
      [key, new DefaultCacheData(key, attributes, [:])]
    }
    def keyCustom = Keys.getAlarmKey(ACCOUNT, REGION, alarmCustom.alarmArn(), "")
    def attrsCustom = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(alarmCustom, ACCOUNT, REGION)
    def keysCustom = [[keyCustom, new DefaultCacheData(keyCustom, attrsCustom, [:])]]

    cacheView.filterIdentifiers(Keys.Namespace.ALARMS.ns, Keys.getAlarmKey(ACCOUNT, REGION, "*", ecsClusterName)) >> keys*.first()
    cacheView.filterIdentifiers(Keys.Namespace.ALARMS.ns, Keys.getAlarmKey(ACCOUNT, REGION, "*", "")) >> keysCustom*.first()
    def combinedMetricIds = Stream.of(keys*.first(), keysCustom*.first())
      .filter { it != null }
      .flatMap { it.stream() }
      .collect(Collectors.toList())
    cacheView.getAll(Keys.Namespace.ALARMS.ns, combinedMetricIds) >> keys*.last() + keysCustom*.last()

    when:
    def metricAlarmsReturned = client.getMetricAlarms(serviceName, ACCOUNT, REGION, ecsClusterName)

    then:
    metricAlarmsReturned.size() == 3
    metricAlarmsReturned*.alarmName.containsAll(["alarm-name", "alarm-name-2", "alarm-name-2-custom"])
    metricAlarmsReturned*.alarmArn.containsAll(["alarmArn", "alarmArn2", "alarmArn2-custom"])
    !metricAlarmsReturned*.alarmArn.contains(["alarmArn3"])
  }
}
