/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.dryrun

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.it
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule

object RoundingSerializerTest : Spek({
  val mapper = JsonMapper.builder()
    .addModule(
      SimpleModule().apply {
        addSerializer(Double::class.javaObjectType, RoundingDoubleSerializer())
        addSerializer(Float::class.javaObjectType, RoundingFloatSerializer())
      }
    )
    .build()

  it("serializes integral floating point values as integers") {
    assertThat(mapper.writeValueAsString(3.0)).isEqualTo("3")
    assertThat(mapper.writeValueAsString(3.0f)).isEqualTo("3")
  }

  it("preserves fractional floating point values") {
    assertThat(mapper.writeValueAsString(3.5)).isEqualTo("3.5")
    assertThat(mapper.writeValueAsString(3.5f)).isEqualTo("3.5")
  }
})
