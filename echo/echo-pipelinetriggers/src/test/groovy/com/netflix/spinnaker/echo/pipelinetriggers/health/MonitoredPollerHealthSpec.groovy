package com.netflix.spinnaker.echo.pipelinetriggers.health

import com.netflix.spinnaker.echo.pipelinetriggers.MonitoredPoller
import com.netflix.spinnaker.echo.pipelinetriggers.health.MonitoredPollerHealth
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static java.time.Instant.now
import static org.springframework.boot.actuate.health.Status.*

class MonitoredPollerHealthSpec extends Specification {

  def poller = Stub(MonitoredPoller) {
    getPollingIntervalSeconds() >> 30
  }

  @Subject health = new MonitoredPollerHealth(poller)

  @Shared goodTimestamp = now().minusSeconds(30)
  @Shared badTimestamp = now().minusSeconds(61)

  @Unroll
  def "when poller is #runningDescription and #timestampDescription health is #status"() {
    given:
    poller.isRunning() >> running
    poller.getLastPollTimestamp() >> lastPollTimestamp
    poller.isInitialized() >> (lastPollTimestamp != null)

    expect:
    health.health().status == status

    where:
    running | lastPollTimestamp || status
    false   | null              || DOWN
    false   | goodTimestamp     || UP
    true    | null              || DOWN
    true    | goodTimestamp     || UP
    true    | badTimestamp      || UP

    runningDescription = running ? "running" : "not running"
    timestampDescription = lastPollTimestamp ? "it last polled at ${lastPollTimestamp}" : "it has never polled"
  }
}
