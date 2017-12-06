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

import com.netflix.spinnaker.igor.polling.PollingMonitor
import groovy.util.logging.Slf4j
import org.joda.time.DateTimeConstants
import org.springframework.beans.BeansException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.Status
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

/**
 * The health status is based on whether the poller is running and how long ago it last polled. If twice the polling
 * interval has passed since the last poll the poller is considered _down_.
 */
@Component
@Slf4j
public class PollingMonitorHealth implements HealthIndicator {

    @Autowired
    ApplicationContext applicationContext

    @Override
    public Health health() {
        List<Health> healths = []
        pollingMonitors.forEach { poller ->
            if (poller.isInService()) {
                if (poller.lastPoll == null) {
                    healths << Health.unknown().withDetail("${poller.name}.status", 'not polling yet').build()
                } else {
                    // Check if twice the polling interval has elapsed.
                    if (System.currentTimeMillis() - poller.lastPoll > (poller.pollInterval * 5 * DateTimeConstants.MILLIS_PER_SECOND)) {
                        healths << Health.down().withDetail("${poller.name}.status", 'stopped').withDetail("${poller.name}.lastPoll", poller.lastPoll.toString()).build()
                    } else {
                        healths << Health.up().withDetail("${poller.name}.status", 'running').withDetail("${poller.name}.lastPoll", poller.lastPoll.toString()).build()
                    }
                }
            } else {
                healths << Health.unknown().withDetail("${poller.name}.status", 'not in service').build()
            }

        }

        def health = healths.empty ? Health.down() :
            healths.find { it.status == Status.DOWN } ? Health.down() :
                healths.find { it.status == Status.UNKNOWN } ? Health.unknown() :
                    Health.up()


        healths.empty ? health.withDetail('status', 'No polling agents running') :
            healths.forEach {
                it.details.forEach { k, v ->
                    health = health.withDetail(k, v)
                }
            }

        return health.build()
    }

    private List<PollingMonitor> getPollingMonitors() {
        try {
            return applicationContext.getBeansOfType(PollingMonitor.class).values().toList()
        } catch (BeansException e) {
            log.error("Could not get polling monitors", e)
            return []
        }
    }
}
