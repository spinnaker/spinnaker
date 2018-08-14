/*
 * Copyright 2018 Netflix, Inc.
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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.igor.docker.DockerMonitor
import com.netflix.spinnaker.igor.jenkins.JenkinsBuildMonitor
import com.netflix.spinnaker.igor.polling.PollingMonitor
import org.springframework.boot.actuate.health.Status
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean

class PollingMonitorHealthSpec extends Specification {
    def applicationContext = Mock(ApplicationContext)
    def jenkinsBuildMonitor = Mock(JenkinsBuildMonitor)
    def dockerMonitor = Mock(DockerMonitor)
    def registry = new NoopRegistry()
    def pollingMonitorHealth = new PollingMonitorHealth(registry, applicationContext)

    final static clock = Clock.systemDefaultZone()
    final static sixMinsAgo = Instant.now().minus(6, ChronoUnit.MINUTES).toEpochMilli()
    final static now = clock.millis()

    @Unroll
    def "should get pollers health: #description"() {
        given:
        pollingMonitorHealth.upOnce = new AtomicBoolean(upOnce)
        with(jenkinsBuildMonitor) {
            getPollInterval() >> 60 //seconds
            getLastPoll() >> jenkinsLastPoll
            isInService() >> true
            getName() >> "jenkinsBuildMonitor"
        }

        with(dockerMonitor) {
            getPollInterval() >> 60 //seconds
            getLastPoll() >> dockerLastPoll
            isInService() >> true
            getName() >> "dockerMonitor"
        }

        applicationContext.getBeansOfType(PollingMonitor) >> [jenkins: jenkinsBuildMonitor, docker: dockerMonitor]


        expect:
        pollingMonitorHealth.health().status == healthStatus

        where:
        description                         | upOnce | jenkinsLastPoll | dockerLastPoll || healthStatus
        "up initially"                      | false  | null            | null           || Status.UP
        "all pollers are healthy"           | true   | now             | now            || Status.UP
        "up initially, pollers unhealthy"   | true   | sixMinsAgo      | sixMinsAgo     || Status.DOWN
        "up initially, one poller unhealthy"| true   | now             | sixMinsAgo     || Status.DOWN // it has been 5x PollInterval
    }
}
