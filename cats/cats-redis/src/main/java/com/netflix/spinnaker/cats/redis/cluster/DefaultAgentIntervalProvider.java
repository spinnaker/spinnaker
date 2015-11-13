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

package com.netflix.spinnaker.cats.redis.cluster;

import com.netflix.spinnaker.cats.agent.Agent;

public class DefaultAgentIntervalProvider implements AgentIntervalProvider {
    private final long interval;
    private final long timeout;

    public DefaultAgentIntervalProvider(long interval) {
        this(interval, interval * 2);
    }

    public DefaultAgentIntervalProvider(long interval, long timeout) {
        this.interval = interval;
        this.timeout = timeout;
    }

    @Override
    public Interval getInterval(Agent agent) {
        return new Interval(interval, timeout);
    }
}
