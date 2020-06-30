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
package com.netflix.spinnaker.cats.sql.cache

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.lang.IllegalArgumentException
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class SqlNamesTest : JUnit5Minutests {

  fun tests() = rootContext<SqlNames> {
    fixture {
      SqlNames()
    }

    listOf(
      TableName("hello", "", "cats_v1_hello"),
      TableName("hello", "world", "cats_v1_helloworld"),
      TableName("abcdefghij".repeat(10), "", "cats_v1_abcdefghijabcdefghijabcdefghijabcdefghijaa7d0fee7e891a66"),
      TableName("abcdefghij".repeat(10), "_rel", "cats_v1_abcdefghijabcdefghijabcdefghijabcdef9246690b33571ecc_rel"),
      TableName("abcdefghij".repeat(10), "suffix".repeat(10), "cats_v1_abcdefghijabcdefghijabcdefghijabcdefghijfe546a736182e553")
    ).forEach { table ->
      test("max length of table name is checked: $table") {
        expectThat(checkTableName("cats_v1_", table.name, table.suffix))
          .isEqualTo(table.expected)
      }
    }
  }

  fun agentTests() = rootContext<SqlNames> {
    fixture {
      SqlNames()
    }
    listOf(
      Pair(null, null),
      Pair("myagent", "myagent"),
      Pair("abcdefghij".repeat(20),
        "abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdebb43b982e477772faa2e899f65d0a86b"),
      Pair("abcdefghij".repeat(10) + ":" + "abcdefghij".repeat(10),
        "abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghij:20f5a9d8d3f4f18cfec8a40eda"),
      Pair("abcdefghij:" + "abcdefghij".repeat(20),
        "abcdefghij:abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcd5bfa5c163877f247769cd6b488dff339")
    ).forEach { test ->
      test("max length of table name is checked: ${test.first}") {
        expectThat(checkAgentName(test.first))
          .isEqualTo(test.second)
      }
    }

    test("do not accept types that are too long") {
      expect {
        that(kotlin.runCatching { checkAgentName("abcdefghij".repeat(20) + ":abcdefghij") }
          .exceptionOrNull()).isA<IllegalArgumentException>()
      }
    }
  }

  private inner class TableName(
    val name: String,
    val suffix: String,
    val expected: String
  )
}
