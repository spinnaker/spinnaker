/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.front50.migrations

import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CloudProvidersStringMigrationSpec extends Specification {

  ApplicationDAO applicationDAO = Mock(ApplicationDAO)
  Clock clock = Mock(Clock)

  @Subject
  CloudProvidersStringMigration migration = new CloudProvidersStringMigration(clock: clock, applicationDAO: applicationDAO)

  @Unroll
  def "should set cloudProviders to a comma-separated string if it is a list"() {
    given:
    Application application = new Application(name: "foo", details: [cloudProviders: original], dao: applicationDAO)

    when:
    migration.run()

    then:
    _ * applicationDAO.all() >> [application]
    1 * applicationDAO.update('FOO', { it.details().cloudProviders == expected })
    _ * clock.instant() >> { Instant.ofEpochMilli(Date.parse("yyyy-MM-dd", "2019-01-24").getTime()) }

    where:
    original  || expected
    ["a"]     || "a"
    ["a","b"] || "a,b"
  }

  @Unroll
  def "should not try to update cloudProviders if not a list"() {
    given:
    Application application = new Application(name: "foo", details: [cloudProviders: original], dao: applicationDAO)

    when:
    migration.run()

    then:
    _ * applicationDAO.all() >> [application]
    0 * applicationDAO.update(_, _)
    _ * clock.instant() >> { LocalDate.parse("2019-01-24").atStartOfDay(ZoneId.of("America/Los_Angeles")).toInstant() }

    where:
    original << ["a", "a,b", null]
  }

  @Unroll
  def "should only be valid until 01 June 2019"() {
    when:
    migration.isValid() == isValid

    then:
    clock.instant() >> { LocalDate.parse(date).atStartOfDay(ZoneId.of("America/Los_Angeles")).toInstant() }
    0 * _

    where:
    date         || isValid
    "2019-06-02" || false
    "2019-06-01" || true
    "2019-02-02" || true
  }
}
