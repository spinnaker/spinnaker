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
import strikt.api.expectThat
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

  private inner class TableName(
    val name: String,
    val suffix: String,
    val expected: String
  )
}
