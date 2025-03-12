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

package com.netflix.spinnaker.clouddriver.cache;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.DefaultAgentIntervalProvider;

public class CustomSchedulableAgentIntervalProvider extends DefaultAgentIntervalProvider {

  public CustomSchedulableAgentIntervalProvider(long interval, long errorInterval, long timeout) {
    super(interval, errorInterval, timeout);
  }

  @Override
  public AgentIntervalProvider.Interval getInterval(Agent agent) {
    if (agent instanceof CustomScheduledAgent) {
      CustomScheduledAgent customAgent = (CustomScheduledAgent) agent;
      return getCustomInterval(customAgent);
    }
    return super.getInterval(agent);
  }

  AgentIntervalProvider.Interval getCustomInterval(CustomScheduledAgent agent) {
    final long pollInterval =
        agent.getPollIntervalMillis() == -1 ? super.getInterval() : agent.getPollIntervalMillis();
    final long errorInterval =
        agent.getErrorIntervalMillis() == -1
            ? super.getErrorInterval()
            : agent.getErrorIntervalMillis();
    final long timeoutMillis =
        agent.getTimeoutMillis() == -1 ? super.getTimeout() : agent.getTimeoutMillis();
    return new AgentIntervalProvider.Interval(pollInterval, errorInterval, timeoutMillis);
  }
}
