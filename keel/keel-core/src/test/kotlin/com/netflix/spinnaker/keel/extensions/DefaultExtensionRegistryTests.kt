/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.keel.extensions

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLMapper

class DefaultExtensionRegistryTests {
  @Test
  fun `extensions registered after a read can be deserialized`() {
    for (mapper in listOf(configuredObjectMapper(), configuredYamlMapper())) {
      val registry = DefaultExtensionRegistry(listOf(mapper))
      registry.register(TestExtension::class.java, FirstExtension::class.java, "first")
      assertThat(read(mapper, FirstExtension("one"))).isEqualTo(FirstExtension("one"))
      registry.register(TestExtension::class.java, SecondExtension::class.java, "second")
      assertThat(read(mapper, SecondExtension("two"))).isEqualTo(SecondExtension("two"))
      assertThat(read(mapper, FirstExtension("one"))).isEqualTo(FirstExtension("one"))
    }
  }

  @Test
  fun `registered extensions survive a mapper rebuild`() {
    for (mapper in listOf(configuredObjectMapper(), configuredYamlMapper())) {
      val registry = DefaultExtensionRegistry(listOf(mapper))
      registry.register(TestExtension::class.java, FirstExtension::class.java, "first")
      registry.register(OtherExtension::class.java, OtherFirstExtension::class.java, "first")
      val rebuilt = when (mapper) {
        is JsonMapper -> mapper.rebuild().build()
        is YAMLMapper -> mapper.rebuild().build()
        else -> error("Unexpected mapper ${mapper.javaClass}")
      }
      assertThat(read(rebuilt, FirstExtension("one"))).isEqualTo(FirstExtension("one"))
      val otherJson = rebuilt.writeValueAsString(OtherFirstExtension("other"))
      assertThat(rebuilt.readValue(otherJson, OtherExtension::class.java)).isEqualTo(OtherFirstExtension("other"))
    }
  }

  private fun read(mapper: ObjectMapper, value: TestExtension): TestExtension =
    mapper.readValue(mapper.writeValueAsString(value), TestExtension::class.java)

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  interface TestExtension

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  interface OtherExtension

  data class OtherFirstExtension(val value: String) : OtherExtension
  data class FirstExtension(val value: String) : TestExtension
  data class SecondExtension(val value: String) : TestExtension
}
