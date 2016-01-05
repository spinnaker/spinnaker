/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.eureka

import com.netflix.discovery.DiscoveryClient
import org.springframework.context.ApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import rx.schedulers.Schedulers
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.*
import static java.util.concurrent.TimeUnit.SECONDS

class DiscoveryStatusPollerSpec extends Specification {

  static final long TICK = 1L
  def discoveryClient = Stub(DiscoveryClient)
  def scheduler = Schedulers.test()
  def context = Mock(ApplicationContext)
  @Subject poller = new DiscoveryStatusPoller(
    discoveryClient,
    TICK,
    scheduler,
    context
  )

  def "emits an event every time the application status changes"() {
    given:
    startPolling()

    and:
    discoveryClient.getInstanceRemoteStatus() >>> statuses

    when:
    tick(expectedEvents)

    then:
    expectedEvents * context.publishEvent(_)

    where:
    statuses = [STARTING, UP, OUT_OF_SERVICE]
    expectedEvents = statuses.size()
  }

  def "does not emit an event when the application status has not changed"() {
    given:
    startPolling()

    and:
    discoveryClient.getInstanceRemoteStatus() >> status

    and:
    tick()

    when:
    tick()

    then:
    0 * context.publishEvent(_)

    where:
    status = UP
  }

  def "emits the previous status alongside the new"() {
    given:
    startPolling()

    and:
    discoveryClient.getInstanceRemoteStatus() >> oldStatus >> newStatus

    and:
    tick()

    and:
    def publishedEvent = null
    context.publishEvent(_) >> { publishedEvent = it[0] }

    when:
    tick()

    then:
    with(publishedEvent.statusChangeEvent) {
      current == newStatus
      previous == oldStatus
    }

    where:
    oldStatus = STARTING
    newStatus = UP
  }

  def "does not start polling until the application context is ready"() {
    given:
    discoveryClient.getInstanceRemoteStatus() >> statuses

    when:
    tick(statuses.size())

    then:
    0 * context.publishEvent(_)

    where:
    statuses = [STARTING, UP, OUT_OF_SERVICE]
  }

  def "is resilient to failures when talking to Eureka"() {
    given:
    startPolling()

    and:
    discoveryClient.getInstanceRemoteStatus() >>
      STARTING >>
      { throw new IllegalStateException() } >>
      UP

    when:
    tick(3)

    then:
    2 * context.publishEvent(_)
  }

  private void startPolling() {
    poller.onApplicationEvent(new ContextRefreshedEvent(context))
  }

  private void tick(int ticks = 1) {
    scheduler.advanceTimeBy(ticks * TICK, SECONDS)
  }
}
