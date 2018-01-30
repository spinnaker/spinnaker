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

package com.netflix.spinnaker.clouddriver.ecs.services

import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsCloudWatchAlarmCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsMetricAlarm
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification
import spock.lang.Subject

class EcsCloudMetricServiceSpec extends Specification {
  def metricAlarmCacheClient = Mock(EcsCloudWatchAlarmCacheClient)
  def accountCredentialsProvider = Mock(AccountCredentialsProvider)
  def amazonClientProvider = Mock(AmazonClientProvider)

  @Subject
  def service = new EcsCloudMetricService()

  def 'should delete metric alarms'() {
    given:
    def creds = TestCredential.named('test')
    def region = creds.getRegions()[0].getName()
    def serviceName = 'test-kcats-liated'

    def metricAlarms = []
    5.times {
      metricAlarms << new EcsMetricAlarm(
        accountName: creds.getName(),
        region: region
      )
    }

    def amazonCloudWatch = Mock(AmazonCloudWatch)

    service.amazonClientProvider = amazonClientProvider
    service.accountCredentialsProvider = accountCredentialsProvider
    service.metricAlarmCacheClient = metricAlarmCacheClient

    accountCredentialsProvider.getCredentials(_) >> creds
    amazonClientProvider.getAmazonCloudWatch(_, _, _) >> amazonCloudWatch
    metricAlarmCacheClient.getMetricAlarms(_, _, _) >> metricAlarms


    when:
    service.deleteMetrics(serviceName, creds.getName(), region)

    then:
    1 * amazonCloudWatch.deleteAlarms(_)
  }
}
