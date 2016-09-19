/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class DateUtils {

  /**
   * Parses a date time string in ISO_LOCAL_DATE_TIME format.
   *
   * It is assumed the date time string does not include a timezone offset, as is the norm for Openstack starting with
   * Liberty and later.
   * @param time the date time string to parse
   * @param defaultTime a default time to use if the given date time is null, defaults to Now
   * @return a parsed date time object
   */
  static ZonedDateTime parseZonedDateTime(String time, ZonedDateTime defaultTime = null) {
    ZonedDateTime result
    if (time) {
      result = LocalDateTime.parse(time, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(ZoneId.systemDefault())
    } else {
      result = defaultTime ?: ZonedDateTime.now()
    }
    result
  }
}

