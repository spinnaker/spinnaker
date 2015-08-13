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

    expect:
    health.health().status == status

    where:
    running | lastPollTimestamp || status
    false   | null              || OUT_OF_SERVICE
    false   | goodTimestamp     || OUT_OF_SERVICE
    true    | null              || UNKNOWN
    true    | goodTimestamp     || UP
    true    | badTimestamp      || DOWN

    runningDescription = running ? "running" : "not running"
    timestampDescription = lastPollTimestamp ? "it last polled at ${lastPollTimestamp}" : "it has never polled"
  }

}
