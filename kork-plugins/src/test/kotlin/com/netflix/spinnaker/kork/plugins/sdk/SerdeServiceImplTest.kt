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
package com.netflix.spinnaker.kork.plugins.sdk

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.kork.plugins.sdk.serde.SerdeServiceImpl
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class SerdeServiceImplTest : JUnit5Minutests {

  fun tests() = rootContext<SerdeServiceImpl> {
    fixture {
      SerdeServiceImpl(ObjectMapper().registerModule(KotlinModule()))
    }
    test("map to converts a hashmap to target type") {
      val o = mapOf(
        "foo" to "hello",
        "bar" to 12
      )
      expectThat(mapTo(o, MyType::class.java)).and {
        get { foo }.isEqualTo("hello")
        get { bar }.isEqualTo(12)
      }
    }

    test("map to converts part of a hashmap to target type") {
      val o = mapOf(
        "nested" to mapOf(
          "foo" to "hello",
          "bar" to 12
        )
      )
      expectThat(mapTo("/nested", o, MyType::class.java)).and {
        get { foo }.isEqualTo("hello")
        get { bar }.isEqualTo(12)
      }
    }
  }

  private class MyType(
    val foo: String,
    val bar: Int
  )
}
