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

package com.netflix.spinnaker.kork.plugins.pluginref

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.kork.exceptions.UserException
import org.pf4j.Plugin
import org.pf4j.PluginDescriptor
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A [PluginRef] is a type of [Plugin] that exists as a pointer to an actual Plugin for use
 * during Plugin development.
 *
 * This class includes helper methods via its companion object to support this as a JSON
 * document with a .plugin-ref extension.
 *
 * The intention of [PluginRef] is to allow a runtime experience similar to dropping a fully
 * packaged plugin into the host application, without requiring the packaging and deployment
 * step (aside from a one time generation and copy/link of the [PluginRef] file).
 */
data class PluginRef(
  /**
   * The path to the concrete [Plugin] implementation.
   *
   * This path will be used to locate a [PluginDescriptor] for the [Plugin].
   */
  val pluginPath: String,

  /**
   * A list of directories containing compiled class-files for the [Plugin].
   *
   * These directories should match the build output directories in the [Plugin]'s
   * development workspace or IDE.
   */
  val classesDirs: List<String>,

  /**
   * A list of directories containing jars scoped to the [Plugin].
   *
   * These jars should be referenced from the [Plugin]'s development workspace or IDE.
   */
  val libsDirs: List<String>
) {
  companion object {
    const val EXTENSION = ".plugin-ref"

    private val mapper = jacksonObjectMapper()

    fun isPluginRef(path: Path?): Boolean =
      path != null && Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(EXTENSION)

    fun loadPluginRef(path: Path?): PluginRef {
      if (!isPluginRef(path)) {
        throw InvalidPluginRefException(path)
      }

      try {
        val ref = mapper.readValue(path!!.toFile(), PluginRef::class.java)
        return if (ref.refPath.isAbsolute) {
          ref
        } else {
          ref.copy(pluginPath = path.parent.resolve(ref.refPath).toAbsolutePath().toString())
        }
      } catch (ex: IOException) {
        throw MalformedPluginRefException(path!!, ex)
      }
    }
  }

  val refPath: Path
    @JsonIgnore
    get() = Paths.get(pluginPath)
}

class InvalidPluginRefException(path: Path?) :
  UserException(path?.let { "${it.fileName} is not a plugin-ref file" } ?: "Null path passed as plugin-ref")

class MalformedPluginRefException(path: Path, cause: Throwable) :
  UserException("${path.fileName} is not a valid plugin-ref", cause)
