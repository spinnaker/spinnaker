package com.netflix.spinnaker.orca.clouddriver.utils

import com.netflix.spinnaker.orca.clouddriver.model.HealthState
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.clouddriver.model.HealthState.*


class HealthHelperSpec extends Specification {
  def health(HealthState state, String type = "SomeHealth", healthClass = "SomeHealthClass") {
    return [state: state.toString(), type: type, healhClass: healthClass]
  }

  def instance(List<HealthState> healthStates) {
    return [health: healthStates.collect({ it -> health(it)})]
  }

  @Unroll
  def "someAreUpAndNoneAreDownOrStarting(#healths) is #expected"() {
    when:
    def actual = HealthHelper.someAreUpAndNoneAreDownOrStarting(instance(healths), null)

    then:
    actual == expected

    where:
    healths                 || expected
    [Up]                    || true

    // Unknown is up-neutral, it is not up by itself, but it does not block up checks
    [Unknown]               || false
    [Up, Unknown]           || true

    // OutOfService, Down, Starting, Draining are up-negative, they do block up checks
    [OutOfService]          || false
    [Up, OutOfService]      || false

    [Down]                  || false
    [Up, Down]              || false

    [Starting]              || false
    [Up, Starting]          || false

    [Draining]              || false
    [Up, Draining]          || false
  }

  @Unroll
  def "someAreDownAndNoneAreUp(#healths) is #expected"() {
    when:
    def actual = HealthHelper.someAreDownAndNoneAreUp(instance(healths), null)

    then:
    actual == expected

    where:
    healths                  || expected
    // No health, Down, OutOfService and Starting are down-positive, i.e. they are by themselves considered down
    []                       || true
    [Down]                   || true
    [OutOfService]           || true
    [Starting]               || true

    // Unknown is down-neutral, it is not down by itself, but it does not block down checks
    [Unknown]                || false
    [Down, Unknown]          || true

    // Up and Draining are down-negative, they do block down checks
    [Up]                     || false
    [Down, Up]               || false
    [OutOfService, Up]       || false
    [Starting, Up]           || false

    [Draining]               || false
    [Down, Draining]         || false
    [OutOfService, Draining] || false
    [Starting, Draining]     || false
  }
}
