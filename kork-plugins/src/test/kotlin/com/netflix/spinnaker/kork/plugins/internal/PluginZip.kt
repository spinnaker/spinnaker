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
package com.netflix.spinnaker.kork.plugins.internal

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.pf4j.PropertiesPluginDescriptorFinder

class PluginZip(
  val path: Path,
  val pluginId: String,
  val pluginClass: String?,
  val pluginVersion: String?
) {

  val file: File = path.toFile()

  fun unzippedPath(): Path {
    val fileName = path.fileName.toString()

    return path.parent.resolve(fileName.substring(0, fileName.length - 4)) // without ".zip" suffix
  }

  class Builder(
    private val path: Path,
    private val pluginId: String
  ) {

    private lateinit var pluginClass: String
    private lateinit var pluginVersion: String
    private val properties: MutableMap<String, String> = mutableMapOf()
    private val files: MutableMap<Path, ByteArray> = mutableMapOf()

    fun pluginClass(pluginClass: String): Builder {
      this.pluginClass = pluginClass
      return this
    }

    fun pluginVersion(pluginVersion: String): Builder {
      this.pluginVersion = pluginVersion
      return this
    }

    /**
     * Add extra properties to the `properties` file.
     * As possible attribute name please see [PropertiesPluginDescriptorFinder].
     */
    fun properties(properties: Map<String, String>): Builder {
      this.properties.putAll(properties)
      return this
    }

    /**
     * Add extra property to the `properties` file.
     * As possible property name please see [PropertiesPluginDescriptorFinder].
     */
    fun property(name: String, value: String): Builder {
      properties[name] = value
      return this
    }

    /**
     * Adds a file to the archive.
     *
     * @param path the relative path of the file
     * @param content the content of the file
     */
    fun addFile(path: Path, content: ByteArray): Builder {
      files[path] = content.clone()
      return this
    }

    /**
     * Adds a file to the archive.
     *
     * @param path the relative path of the file
     * @param content the content of the file
     */
    fun addFile(path: Path, content: String): Builder {
      files[path] = content.toByteArray()
      return this
    }

    fun build(): PluginZip {
      createPropertiesFile()
      return PluginZip(path, pluginId, pluginClass, pluginVersion)
    }

    private fun createPropertiesFile() {
      val map = mutableMapOf<String, String>()
      map[PropertiesPluginDescriptorFinder.PLUGIN_ID] = pluginId
      map[PropertiesPluginDescriptorFinder.PLUGIN_VERSION] = pluginVersion
      map[PropertiesPluginDescriptorFinder.PLUGIN_CLASS] = pluginClass
      map.putAll(properties)

      ZipOutputStream(FileOutputStream(path.toFile())).use { outputStream ->
        val propertiesFile = ZipEntry(PropertiesPluginDescriptorFinder.DEFAULT_PROPERTIES_FILE_NAME)
        outputStream.putNextEntry(propertiesFile)
        createProperties(map).store(outputStream, "")
        outputStream.closeEntry()

        for (fileEntry in files.entries) {
          val file = ZipEntry(fileEntry.key.toString())
          outputStream.putNextEntry(file)
          outputStream.write(fileEntry.value)
          outputStream.closeEntry()
        }
      }
    }
  }

  companion object {
    fun createProperties(map: Map<String, String>): Properties {
      val properties = Properties()
      properties.putAll(map)

      return properties
    }
  }
}
