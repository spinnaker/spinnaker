/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.test.mimicker.producers

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.security.SecureRandom
import strikt.api.expectThat
import strikt.assertions.isNotSameInstanceAs

class MonikerProducerTest : JUnit5Minutests {

  fun tests() = rootContext<MonikerProducer> {
    fixture {
      MonikerProducer(RandomProducer(SecureRandom()))
    }

    test("get creates new moniker") {
      expectThat(get()).isNotSameInstanceAs(get())
    }
  }
}
