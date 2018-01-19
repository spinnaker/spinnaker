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

package com.netflix.spinnaker.clouddriver.ecs.controllers

import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsMetricAlarm
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsCloudMetricProvider
import spock.lang.Specification
import spock.lang.Subject

class EcsCloudMetricControllerSpec extends Specification {
  def provider = Mock(EcsCloudMetricProvider)
  @Subject
  def controller = new EcsCloudMetricController(provider)

  def 'should get a map of metrics'() {
    given:
    def metricAlarms = [new EcsMetricAlarm().withAlarmName('alarm-name-1').withAlarmArn('alarm-arn-1'),
                        new EcsMetricAlarm().withAlarmName('alarm-name-2').withAlarmArn('alarm-arn-2')]

    when:
    def returnedMetricAlarms = controller.findAllMetricAlarms()

    then:
    provider.getAllMetricAlarms() >> metricAlarms
    metricAlarms*.getAlarmArn().containsAll(returnedMetricAlarms*.getAlarmArn())
    metricAlarms*.getAlarmName().containsAll(returnedMetricAlarms*.getAlarmName())

  }
}
