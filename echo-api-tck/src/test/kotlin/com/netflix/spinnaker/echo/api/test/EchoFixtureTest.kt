/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.echo.api.test

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext

class EchoFixtureTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    context("an echo integration test environment") {
      echoFixture {
        Fixture()
      }

      test("service starts") { /* no-op */ }
    }
  }

  inner class Fixture : EchoFixture()
}
