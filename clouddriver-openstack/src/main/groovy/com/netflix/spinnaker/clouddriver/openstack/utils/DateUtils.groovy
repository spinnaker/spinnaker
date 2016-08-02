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

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class DateUtils {

  static ZonedDateTime cascadingParseDateTime(String dateTime) {
    ZonedDateTime result = handleParseException {
      ZonedDateTime.parse(dateTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    if (!result) {
      result = handleParseException {
        LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)?.atZone(ZoneId.systemDefault())
      }
    }

    result
  }

  static <T> T handleParseException(Closure<T> closure) {
    T result
    try {
      result = closure.call()
    } catch (DateTimeParseException e) {
      //Do nothing
    }
  }
}
