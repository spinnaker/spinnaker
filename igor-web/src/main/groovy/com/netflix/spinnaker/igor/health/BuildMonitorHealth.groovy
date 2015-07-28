/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License')
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.health

import com.netflix.spinnaker.igor.jenkins.BuildMonitor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

/**
 * The health status is based on whether the poller is running and how long ago it last polled. If twice the polling
 * interval has passed since the last poll the poller is considered _down_.
 */
@Component
public class BuildMonitorHealth implements HealthIndicator {

    @Autowired
    BuildMonitor poller

    @Override
    public Health health() {
        if (poller.isInService()) {
            if (poller.lastPoll == null) {
                Health.unknown().withDetail('buildmonitor.status', 'not polling yet').build()
            } else {
                if (System.currentTimeMillis() - poller.lastPoll > (poller.pollInterval * 2 * 1000)) {
                    Health.down().withDetail('buildmonitor.status', 'stopped').withDetail('buildmonitor.lastPoll', "${poller.lastPoll}").build()
                } else {
                    Health.up().withDetail('buildmonitor.status', 'running').withDetail('buildmonitor.lastPoll', "${poller.lastPoll}").build()
                }
            }
        } else {
            Health.unknown().withDetail('buildmonitor.status', 'not in service').build()
        }
    }

}
