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
 */

package com.netflix.spinnaker.echo.api.events

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expect
import strikt.assertions.isEqualTo

class MetadataTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    context("Metadata") {
      fixture {
        Fixture()
      }

      test("Request header case insensitive") {
        val upperCaseHeader = "UPPERCASE-HEADER"
        val lowerCaseHeader = "lowercase-header"
        metadata.requestHeaders[upperCaseHeader] = mutableListOf("foo")
        metadata.requestHeaders[lowerCaseHeader] = mutableListOf("bar")

        expect {
          that(metadata.requestHeaders[upperCaseHeader.toLowerCase()]?.get(0)).isEqualTo("foo")
          that(metadata.requestHeaders[lowerCaseHeader.toUpperCase()]?.get(0)).isEqualTo("bar")
        }
      }
    }
  }

  private class Fixture {
    var metadata = Metadata()
  }
}
