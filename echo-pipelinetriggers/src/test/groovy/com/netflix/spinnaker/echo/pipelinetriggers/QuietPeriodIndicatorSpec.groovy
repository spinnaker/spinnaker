/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.config.QuietPeriodIndicatorConfigurationProperties
import spock.lang.Specification

import java.time.Instant

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

class QuietPeriodIndicatorSpec extends Specification {
  def registry = new NoopRegistry()
  def goodStartDate = "2018-01-01T00:00:00Z"
  def goodEndDate = "2018-02-01T00:00:00Z"
  def badDate = "flarg"
  def beforeDate = "2000-01-01T00:00:00Z"
  def afterDate = "2019-01-01T00:00:00Z"
  def inRangeDate = "2018-01-19T00:00:00Z"

  def parseIso(String iso) {
    return Instant.from(ISO_INSTANT.parse(iso)).toEpochMilli()
  }

  def "is disabled if 'enabled' is false"() {
    given:
    QuietPeriodIndicatorConfigurationProperties config = new QuietPeriodIndicatorConfigurationProperties(false, goodStartDate, goodEndDate, Collections.emptyList());
    QuietPeriodIndicator quietPeriodIndicator = new QuietPeriodIndicator(registry, config)

    expect:
    !quietPeriodIndicator.isEnabled()
  }

  def "disabled if start date is invalid"() {
    given:
    QuietPeriodIndicatorConfigurationProperties config = new QuietPeriodIndicatorConfigurationProperties(false, badDate, goodEndDate, Collections.emptyList());
    QuietPeriodIndicator quietPeriodIndicator = new QuietPeriodIndicator(registry, config)

    expect:
    !quietPeriodIndicator.isEnabled()
  }

  def "disabled if end date is invalid"() {
    given:
    QuietPeriodIndicatorConfigurationProperties config = new QuietPeriodIndicatorConfigurationProperties(false, goodStartDate, badDate, Collections.emptyList());
    QuietPeriodIndicator quietPeriodIndicator = new QuietPeriodIndicator(registry, config)

    expect:
    !quietPeriodIndicator.isEnabled()
  }

  def "ranges work"() {
    given:
    QuietPeriodIndicatorConfigurationProperties config = new QuietPeriodIndicatorConfigurationProperties(true, goodStartDate, goodEndDate, Collections.emptyList());
    QuietPeriodIndicator quietPeriodIndicator = new QuietPeriodIndicator(registry, config)

    expect:
    !quietPeriodIndicator.inQuietPeriod(parseIso(beforeDate))
    quietPeriodIndicator.inQuietPeriod(parseIso(inRangeDate))
    !quietPeriodIndicator.inQuietPeriod(parseIso(afterDate))
  }

  def "trigger type list is respected"() {
    given:
    ArrayList<String> triggerTypes = new ArrayList<>();
    triggerTypes.add("inTheList")
    QuietPeriodIndicatorConfigurationProperties config = new QuietPeriodIndicatorConfigurationProperties(true, goodStartDate, goodEndDate, triggerTypes);
    QuietPeriodIndicator quietPeriodIndicator = new QuietPeriodIndicator(registry, config)

    expect:
    !quietPeriodIndicator.inQuietPeriod(parseIso(inRangeDate), "notInTheList")
    quietPeriodIndicator.inQuietPeriod(parseIso(inRangeDate), "inTheList")
  }
}
