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
import spock.lang.Unroll

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class DateUtilsSpec extends Specification {

  @Unroll
  void 'test cascading parse - #testCase'() {
    when:
    ZonedDateTime result = DateUtils.cascadingParseDateTime(dateTime)

    then:
    result == expected
    noExceptionThrown()

    where:
    testCase          | dateTime                    | expected
    'zoned date time' | '2011-12-03T10:15:30+01:00' | ZonedDateTime.parse('2011-12-03T10:15:30+01:00', DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    'local date time' | '2011-12-03T10:15:30'       | LocalDateTime.parse('2011-12-03T10:15:30', DateTimeFormatter.ISO_LOCAL_DATE_TIME)?.atZone(ZoneId.systemDefault())
  }

  void 'test cascading parse exception'() {
    when:
    DateUtils.cascadingParseDateTime('2011-12-03T101530')

    then:
    thrown(DateTimeParseException)
  }
}
