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

package com.netflix.spinnaker.clouddriver.ecs.provider.view

import com.amazonaws.services.cloudwatch.model.MetricAlarm
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsCloudWatchAlarmCacheClient
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.EcsCloudMetricAlarmCachingAgent
import spock.lang.Specification
import spock.lang.Subject

class EcsCloudMetricProviderSpec extends Specification {
  def cacheView = Mock(Cache)
  EcsCloudWatchAlarmCacheClient client = new EcsCloudWatchAlarmCacheClient(cacheView)
  @Subject
  EcsCloudMetricProvider provider = new EcsCloudMetricProvider(client)

  def 'should get metric alarms'() {
    given:
    def metricAlarm1 = new MetricAlarm().withAlarmName("alarm-name-1").withAlarmArn("alarmArn-1")
    def metricAlarm2 = new MetricAlarm().withAlarmName("alarm-name-2").withAlarmArn("alarmArn-2")
    def attributes1 = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(metricAlarm1, 'account-1', 'region-1')
    def attributes2 = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(metricAlarm2, 'account-2', 'region-2')

    when:
    def metricAlarmCollection = provider.getAllMetricAlarms()

    then:
    cacheView.getAll(_) >> [new DefaultCacheData('key-1', attributes1, [:]), new DefaultCacheData('key-2', attributes2, [:])]

    metricAlarmCollection.size() == 2
    metricAlarmCollection*.getAlarmArn().containsAll([metricAlarm1.getAlarmArn(), metricAlarm2.getAlarmArn()])
    metricAlarmCollection*.getMetricName().containsAll([metricAlarm1.getMetricName(), metricAlarm2.getMetricName()])
    metricAlarmCollection*.getAccountName().containsAll(['account-1', 'account-2'])
    metricAlarmCollection*.getRegion().containsAll(['region-1', 'region-2'])
  }

}
