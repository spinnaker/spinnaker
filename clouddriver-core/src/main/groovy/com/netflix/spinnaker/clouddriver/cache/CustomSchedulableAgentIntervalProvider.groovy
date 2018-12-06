/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.cache

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.cluster.DefaultAgentIntervalProvider
import groovy.transform.CompileStatic

@CompileStatic
class CustomSchedulableAgentIntervalProvider extends DefaultAgentIntervalProvider {

  CustomSchedulableAgentIntervalProvider(long interval, long timeout) {
    super(interval, timeout)
  }

  CustomSchedulableAgentIntervalProvider(long interval, long errorInterval, long timeout) {
    super(interval, errorInterval, timeout)
  }

  @Override
  AgentIntervalProvider.Interval getInterval(Agent agent) {
    if (agent instanceof CustomScheduledAgent) {
      return getCustomInterval(agent)
    }
    return super.getInterval(agent)
  }

  AgentIntervalProvider.Interval getCustomInterval(CustomScheduledAgent agent) {
    final long pollInterval = agent.pollIntervalMillis == -1 ? super.interval : agent.pollIntervalMillis
    final long errorInterval = agent.errorIntervalMillis == -1 ? super.errorInterval : agent.errorIntervalMillis
    final long timeoutMillis = agent.timeoutMillis == -1 ? super.timeout : agent.timeoutMillis
    return new AgentIntervalProvider.Interval(pollInterval, errorInterval, timeoutMillis)
  }
}
