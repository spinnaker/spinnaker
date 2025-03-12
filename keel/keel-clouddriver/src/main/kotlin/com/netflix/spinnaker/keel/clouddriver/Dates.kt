/*
 *
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
 *
 */
package com.netflix.spinnaker.keel.clouddriver

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Dates {
  companion object {
    private val formats: List<DateTimeFormatter> = listOf(
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSS'Z'"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd"),
      DateTimeFormatter.ofPattern("MM/dd/yyyy"),
      DateTimeFormatter.ofPattern("MMMM d, yyyy h:mm a"),
      DateTimeFormatter.ofPattern("MMMM d, yyyy"),
      DateTimeFormatter.ofPattern("MMMM d, yyyy")
    )

    fun toLocalDateTime(date: String): LocalDateTime {
      var exception: Exception? = null
      formats.forEach { format ->
        try {
          return LocalDateTime.parse(date, format)
        } catch (e: Exception) {
          exception = e
        }
      }

      throw exception!!
    }
  }
}
