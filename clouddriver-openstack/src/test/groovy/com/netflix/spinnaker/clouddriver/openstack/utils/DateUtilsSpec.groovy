/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.utils

import spock.lang.Specification

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class DateUtilsSpec extends Specification {


  def 'parse local date time'() {
    given:
    def dateTime = '2011-12-03T10:15:30'
    def expected = LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)?.atZone(ZoneId.systemDefault())

    when:
    ZonedDateTime result = DateUtils.parseZonedDateTime(dateTime)

    then:
    result == expected
    noExceptionThrown()
  }

  def 'default date time'() {
    given:
    def defaultDateTime = ZonedDateTime.now()

    when:
    ZonedDateTime result = DateUtils.parseZonedDateTime(null, defaultDateTime)

    then:
    result == defaultDateTime
    noExceptionThrown()
  }

  def 'the default has a default'() {
    when:
    ZonedDateTime result = DateUtils.parseZonedDateTime(null)

    then:
    /*
     * Can't really verify the actual time of the default's default which is Now, you know, with off by a second,
     * or an hour on with daylight savings and what not. Lets just ensure we got back an object without any exceptions.
     */
    result
    noExceptionThrown()
  }

  def 'parse zoned date time format'() {
    given:
    def time = '2011-12-03T10:15:30+01:00'
    def expected = ZonedDateTime.parse(time, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    when:
    def actual = DateUtils.parseZonedDateTime(time)

    then:
    actual == expected
    noExceptionThrown()
  }

  def 'parse UTC date time'() {
    given:
    def time = '2017-01-18T01:38:53Z'
    def expected = ZonedDateTime.parse(time, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    when:
    def actual = DateUtils.parseZonedDateTime(time)

    then:
    actual == expected
    noExceptionThrown()
  }

  def 'throws exception with unknown format'() {
    when:
    DateUtils.parseZonedDateTime('10:15:30+01:00')

    then:
    thrown(DateTimeParseException)
  }
}
