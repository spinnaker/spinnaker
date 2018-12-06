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

package com.netflix.spinnaker.cats.cluster;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentIntervalAware;

public class DefaultAgentIntervalProvider implements AgentIntervalProvider {
    private final long interval;
    private final long errorInterval;
    private final long timeout;

    public DefaultAgentIntervalProvider(long interval) {
        this(interval, interval * 2);
    }

    public DefaultAgentIntervalProvider(long interval, long timeout) {
        this(interval, interval, timeout);
    }

    public DefaultAgentIntervalProvider(long interval, long errorInterval, long timeout) {
        this.interval = interval;
        this.errorInterval = errorInterval;
        this.timeout = timeout;
    }

    @Override
    public Interval getInterval(Agent agent) {
        if (agent instanceof AgentIntervalAware) {
            Long agentInterval = ((AgentIntervalAware) agent).getAgentInterval();
            Long agentErrorInterval = ((AgentIntervalAware) agent).getAgentErrorInterval();
            if (agentInterval != null && agentInterval > 0) {
                // Specify the caching agent timeout as twice the interval. This gives a high upper bound
                // on the time it should take the agent to complete its work. The agent's lock is revoked
                // after the timeout.
                return new Interval(agentInterval, agentErrorInterval, 2 * agentInterval);
            }
        }

        return new Interval(interval, errorInterval, timeout);
    }

    public long getInterval() {
        return interval;
    }

    public long getErrorInterval() {
        return errorInterval;
    }

    public long getTimeout() {
        return timeout;
    }
}
