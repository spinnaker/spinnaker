/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.state

import com.fasterxml.jackson.databind.ObjectMapper
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import org.junit.jupiter.api.Test

object StateInspectorTest {

  val objectMapper = ObjectMapper()
  val subject = StateInspector(objectMapper)

  @Test
  fun `should find the difference in root-level elements`() {
    val right = model()
    val left = model().apply {
      string = "left"
      boolean = false
      nestedMap = mapOf(
        "one" to mapOf(
          "word" to "one",
          "number" to "1"
        ),
        "two" to mapOf(
          "word" to "changed",
          "number" to "2"
        )
      )
    }

    val diff = subject.getDiff("foo", right, left, MockModel::class, MockSpec::class)

    diff.size shouldMatch equalTo(3)
    diff.find { it.name == "string" } shouldMatch equalTo(
      FieldState(name = "string", current = "right", desired = "left")
    )
    diff.find { it.name == "boolean" } shouldMatch equalTo(
      FieldState(name = "boolean", current = true, desired = false)
    )
    diff.find { it.name == "nestedMap" } shouldMatch equalTo(
      FieldState(
        name = "nestedMap",
        current = mapOf(
          "one" to mapOf(
            "word" to "one",
            "number" to "1"
          ),
          "two" to mapOf(
            "word" to "two",
            "number" to "2"
          )
        ),
        desired = mapOf(
          "one" to mapOf(
            "word" to "one",
            "number" to "1"
          ),
          "two" to mapOf(
            "word" to "changed",
            "number" to "2"
          )
        ))
    )
  }

  private fun model() = MockModel(
    string = "right",
    number = 9000,
    boolean = true,
    list = listOf("1", "2"),
    set = setOf("3", "4"),
    map = mapOf("one" to "1", "two" to "2"),
    nestedMap = mapOf(
      "one" to mapOf(
        "word" to "one",
        "number" to "1"
      ),
      "two" to mapOf(
        "word" to "two",
        "number" to "2"
      )
    )
  )

  data class MockModel(
    var string: String,
    var number: Number,
    var boolean: Boolean,
    var list: List<String>,
    var set: Set<String>,
    var map: Map<String, String>,
    var nestedMap: Map<String, Map<String, String>>
  )

  data class MockSpec(
    var ignorable: String
  ) : ComputedPropertyProvider {
    override fun additionalComputedProperties() = listOf("ignorable")
  }
}
