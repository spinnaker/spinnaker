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
package com.netflix.spinnaker.kork.plugins.internal

import java.io.FileOutputStream
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.pf4j.PropertiesPluginDescriptorFinder

/**
 * ".zip"
 */
private const val ZIP_EXTENSION_LENGTH = 4

/**
 * Write a plugin ZIP to the specified path.  Add JARs via [Builder.addFile]. Supports the following
 * plugin properties:
 *
 * - plugin.id
 * - plugin.version
 * - plugin.class
 *
 * Additional properties can be added via [Builder.properties] and [Builder.property].
 */
class PluginZip private constructor(builder: Builder) {
  /**
   * Path to the plugin ZIP.
   */
  val path: Path

  /**
   * The plugin ID.
   */
  val pluginId: String

  /**
   * The plugin class.
   */
  val pluginClass: String

  /**
   * The plugin version.
   */
  val pluginVersion: String

  /**
   * Path to the unzipped plugin.
   */
  fun unzippedPath(): Path {
    val fileName = path.fileName.toString()
    return path.parent.resolve(fileName.substring(0, fileName.length - ZIP_EXTENSION_LENGTH)) // without ".zip" suffix
  }

  /**
   * Builder for [PluginZip].
   *
   * @param path Where the plugin ZIP will be created
   * @param pluginId The plugin ID
   */
  class Builder(val path: Path, val pluginId: String) {
    /**
     * The plugin class
     */
    lateinit var pluginClass: String

    /**
     * The plugin version
     */
    lateinit var pluginVersion: String

    private val properties: MutableMap<String, String> = mutableMapOf()
    private val files: MutableMap<Path, ByteArray> = mutableMapOf()

    /**
     * Set the plugin class.
     */
    fun pluginClass(pluginClass: String) =
      apply { this.pluginClass = pluginClass }

    /**
     * Set the plugin version.
     */
    fun pluginVersion(pluginVersion: String) =
      apply { this.pluginVersion = pluginVersion }

    /**
     * Add extra properties to the `properties` file.
     * As possible attribute name please see [PropertiesPluginDescriptorFinder].
     */
    fun properties(properties: Map<String, String>) =
      apply { this.properties.putAll(properties) }

    /**
     * Add extra property to the `properties` file.
     * As possible property name please see [PropertiesPluginDescriptorFinder].
     */
    fun property(name: String, value: String) =
      apply { properties[name] = value }

    /**
     * Adds a file to the archive.
     *
     * @param path the relative path of the file
     * @param content the content of the file
     */
    fun addFile(path: Path, content: ByteArray) =
      apply { files[path] = content.clone() }

    /**
     * Adds a file to the archive.
     *
     * @param path the relative path of the file
     * @param content the content of the file
     */
    fun addFile(path: Path, content: String) =
      apply { files[path] = content.toByteArray() }

    /**
     * Build the [PluginZip].
     */
    fun build(): PluginZip {
      createPropertiesFile()
      return PluginZip(this)
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
    /**
     * Create the [PluginZip] properties file manifest.
     */
    fun createProperties(map: Map<String, String>): Properties {
      val properties = Properties()
      properties.putAll(map)

      return properties
    }
  }

  init {
    path = builder.path
    pluginId = builder.pluginId
    pluginClass = builder.pluginClass
    pluginVersion = builder.pluginVersion
  }
}
