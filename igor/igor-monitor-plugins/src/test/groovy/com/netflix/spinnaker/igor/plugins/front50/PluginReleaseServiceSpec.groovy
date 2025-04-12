/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.igor.plugins.front50

import retrofit2.mock.Calls
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class PluginReleaseServiceSpec extends Specification {

  @Shared
  Clock clock = Clock.fixed(Instant.EPOCH.plus(1, ChronoUnit.DAYS), ZoneId.systemDefault())

  Front50Service front50Service = Mock()
  @Subject PluginReleaseService subject = new PluginReleaseService(front50Service)

  @Unroll
  def "gets releases since timestamp"() {
    given:
    PluginInfo plugin1 = new PluginInfo("plugin1", "A pugin", "foo@example.com", [
            release("1.0.0", clock.instant()),
            release("1.0.1", clock.instant().plus(1, ChronoUnit.DAYS))
    ])
    PluginInfo plugin2 = new PluginInfo("plugin2", "A pugin", "foo@example.com", [
            release("2.0.0", clock.instant().plus(2, ChronoUnit.DAYS))
    ])

    and:
    def lastPollTimestamps = [
      plugin1: timestamp,
      plugin2: timestamp
    ]

    when:
    def result = subject.getPluginReleasesSinceTimestamps(lastPollTimestamps)

    then:
    result*.version == expectedVersions
    1 * front50Service.listPluginInfo() >> Calls.response([plugin1, plugin2])

    where:
    timestamp                                  || expectedVersions
    null                                       || ["1.0.0", "1.0.1", "2.0.0"]
    clock.instant().minus(1, ChronoUnit.HOURS) || ["1.0.0", "1.0.1", "2.0.0"]
    clock.instant()                            || ["1.0.1", "2.0.0"]
    clock.instant().plus(1, ChronoUnit.HOURS)  || ["1.0.1", "2.0.0"]
    clock.instant().plus(2, ChronoUnit.DAYS)   || []
  }

  private PluginInfo.Release release(String version, Instant releaseDate) {
    return new PluginInfo.Release(
      version,
      releaseDate.toString(),
      "orca>=0.0.0",
      "http://example.com/file.zip",
      "sha512",
      true,
      clock.instant().toString(),
    )
  }
}
