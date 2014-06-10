/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.data.aws

import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.netflix.spinnaker.oort.data.aws.cachers.AbstractInfrastructureCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.LaunchConfigCachingAgent
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import reactor.event.Event

class LaunchConfigCachingAgentSpec extends AbstractCachingAgentSpec {
  @Override
  AbstractInfrastructureCachingAgent getCachingAgent() {
    new LaunchConfigCachingAgent(Mock(AmazonNamedAccount), "us-east-1")
  }

  void "load new launch configs and remove those that have disappeared since the last run"() {
    setup:
    def launchConfigName1 = "kato-main-v000-123456"
    def launchConfig1 = new LaunchConfiguration().withLaunchConfigurationName(launchConfigName1)
    def launchConfigName2 = "kato-main-v001-123456"
    def launchConfig2 = new LaunchConfiguration().withLaunchConfigurationName(launchConfigName2)
    def result = new DescribeLaunchConfigurationsResult().withLaunchConfigurations([launchConfig1, launchConfig2])

    when:
    agent.load()

    then:
    1 * amazonAutoScaling.describeLaunchConfigurations() >> result
    2 * reactor.notify("newLaunchConfig", _)

    when:
    agent.load()

    then:
    1 * amazonAutoScaling.describeLaunchConfigurations() >> result.withLaunchConfigurations([launchConfig1])
    0 * reactor.notify("newLaunchConfig", _)
    1 * reactor.notify("missingLaunchConfig", _)

    when:
    agent.load()

    then:
    1 * amazonAutoScaling.describeLaunchConfigurations() >> result
    0 * reactor.notify(_, _)
  }

  void "new launch config should save to cache"() {
    setup:
    def launchConfigName = "kato-main-v000-123456"
    def region = "us-east-1"
    def launchConfig1 = new LaunchConfiguration().withLaunchConfigurationName(launchConfigName)

    when:
    ((LaunchConfigCachingAgent)agent).loadNewLaunchConfig(Event.wrap(new LaunchConfigCachingAgent.NewLaunchConfigNotification(launchConfig1, region)))

    then:
    1 * cacheService.put(Keys.getLaunchConfigKey(launchConfigName, region), _)
  }
}
