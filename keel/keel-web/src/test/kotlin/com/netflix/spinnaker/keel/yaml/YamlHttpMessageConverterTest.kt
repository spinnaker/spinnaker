/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.keel.yaml

import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.kork.yaml.YamlHelper
import com.netflix.spinnaker.kork.yaml.YamlParserProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage

class YamlHttpMessageConverterTest {
  @AfterEach
  fun resetLimits() {
    YamlHelper(YamlParserProperties())
  }

  @Test
  fun `HTTP YAML reads preserve legacy scalar values and explicit strings`() {
    val values = read(
      """
      mode: 0644
      capacity: 012
      enabled: yes
      disabled: off
      quoted: "0644"
      tagged: !!str yes
      explicit: !!int 12
      """.trimIndent(),
      Map::class.java
    )

    assertEquals(420, values["mode"])
    assertEquals(10, values["capacity"])
    assertEquals(true, values["enabled"])
    assertEquals(false, values["disabled"])
    assertEquals("0644", values["quoted"])
    assertEquals("yes", values["tagged"])
    assertEquals(12, values["explicit"])
  }

  @Test
  fun `HTTP YAML reads retain Kotlin model support`() {
    assertEquals(Settings(10, true, "0644"), read("capacity: 012\nenabled: on\nlabel: 0644", Settings::class.java))
  }

  @Test
  fun `HTTP YAML rejects malformed legacy scalars even for strings`() {
    assertThrows(HttpMessageNotReadableException::class.java) {
      read("capacity: 1\nenabled: true\nlabel: ._", Settings::class.java)
    }
  }

  @Test
  fun `HTTP YAML honors configured code point limits`() {
    val properties = YamlParserProperties().apply { codePointLimit = 16 }
    YamlHelper(properties)

    assertEquals(mapOf("v" to 10), read("v: 012", Map::class.java))
    assertThrows(HttpMessageNotReadableException::class.java) {
      read("v: " + "x".repeat(64), Map::class.java)
    }
  }

  private fun <T : Any> read(yaml: String, type: Class<T>): T {
    val mapper = configuredYamlMapper().rebuild().build()
    val converter = YamlHttpMessageConverter(mapper)
    val input = MockHttpInputMessage(yaml.toByteArray()).apply {
      headers.contentType = APPLICATION_YAML
    }
    return type.cast(converter.read(type, input))
  }

  data class Settings(val capacity: Int, val enabled: Boolean, val label: String)
}
