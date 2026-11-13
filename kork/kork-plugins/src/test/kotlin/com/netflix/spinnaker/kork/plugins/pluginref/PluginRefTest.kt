/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.kork.plugins.pluginref

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import tools.jackson.core.JacksonException

class PluginRefTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun `malformed JSON is reported as a malformed plugin ref`() {
    val path = Files.writeString(directory.resolve("broken.plugin-ref"), "{ invalid json")

    val exception = assertThrows<MalformedPluginRefException> { PluginRef.loadPluginRef(path) }

    assertThat(exception).hasMessage("broken.plugin-ref is not a valid plugin-ref")
    assertThat(exception.cause).isInstanceOf(JacksonException::class.java)
  }

  @Test
  fun `missing required fields are reported as a malformed plugin ref`() {
    val path = Files.writeString(directory.resolve("missing.plugin-ref"), "{}")

    val exception = assertThrows<MalformedPluginRefException> { PluginRef.loadPluginRef(path) }

    assertThat(exception.cause).isInstanceOf(JacksonException::class.java)
  }

  @Test
  fun `valid plugin refs resolve relative paths and retain directories`() {
    val path = Files.writeString(
      directory.resolve("valid.plugin-ref"),
      """{"pluginPath":"plugin","classesDirs":["classes"],"libsDirs":["libs"]}"""
    )

    val ref = PluginRef.loadPluginRef(path)

    assertThat(ref.refPath).isEqualTo(directory.resolve("plugin").toAbsolutePath())
    assertThat(ref.classesDirs).containsExactly("classes")
    assertThat(ref.libsDirs).containsExactly("libs")
  }
}
