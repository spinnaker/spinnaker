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

import com.netflix.spinnaker.cats.agent.CachingAgent;

public interface AgentIntervalProvider {
    public static class Interval {
        final long interval;
        final long timeout;

        public Interval(long interval, long timeout) {
            this.interval = interval;
            this.timeout = timeout;
        }

        public long getInterval() {
            return interval;
        }

        public long getTimeout() {
            return timeout;
        }
    }

    Interval getInterval(CachingAgent agent);
}
