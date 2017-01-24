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

import groovy.util.logging.Slf4j

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Slf4j
class DateUtils {

  /**
   * Parses a date time string.
   *
   * It tries the following time formats:
   *
   *    ISO_LOCAL_DATE_TIME
   *    ISO_OFFSET_DATE_TIME
   *
   * @param time the date time string to parse
   * @param defaultTime a default time to use if the given date time is null, defaults to Now
   * @return a parsed date time object
   */
  static ZonedDateTime parseZonedDateTime(String time, ZonedDateTime defaultTime = null) {

    if (time) {
      // Try a couple formats because OpenStack keeps change formats. Sigh.

      try {
        // For date time strings that are the local time without a timezone
        return LocalDateTime.parse(time, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(ZoneId.systemDefault())
      } catch (DateTimeParseException e) {
        log.info("Failed to parse datetime ${time} as ISO_LOCAL_DATE_TIME; ${e.message}")
      }

      try {
        // For date time strings that include an offset (or Z which is no offset)
        return ZonedDateTime.parse(time, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
      } catch (DateTimeParseException e) {
        log.info("Failed to parse datetime ${time} as ISO_OFFSET_DATE_TIME")

        // This is the last attempt, rethrow the exception
        throw(e)
      }
    } else {
      return defaultTime ?: ZonedDateTime.now()
    }

  }
}

