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

import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.PrintWriter
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import org.pf4j.ManifestPluginDescriptorFinder

/**
 * Write a plugin JAR to the specified path.  Writes the following manifest attributes:
 *
 * - Plugin-Id
 * - Plugin-Version
 * - Plugin-Class
 *
 * Additional manifest attributes can be provided via [Builder.manifestAttribute]
 */
class PluginJar private constructor(builder: Builder) {
  /**
   * The path to the JAR.
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
   * The [PluginJar] builder
   *
   * @param path Path to the directory that the plugin JAR will be written
   * @param pluginId The plugin ID
   */
  class Builder(val path: Path, val pluginId: String) {
    /**
     * The plugin class.
     */
    lateinit var pluginClass: String

    /**
     * The plugin version.
     */
    lateinit var pluginVersion: String

    private val manifestAttributes: MutableMap<String, String> = mutableMapOf()
    private val extensions: MutableList<String> = mutableListOf()
    private var classDataProvider: ClassDataProvider = DefaultClassDataProvider()

    /**
     * Set the plugin class name.
     */
    fun pluginClass(pluginClass: String) =
      apply { this.pluginClass = pluginClass }

    /**
     * Set the plugin version.
     */
    fun pluginVersion(pluginVersion: String) =
      apply { this.pluginVersion = pluginVersion }

    /**
     * Add a new extension class name.
     */
    fun extension(extensionClassName: String) =
      apply { this.extensions.add(extensionClassName) }

    /**
     * Set multiple extension class names.
     */
    fun extensions(extensionClassNames: MutableList<String>) =
      apply { this.extensions.addAll(extensionClassNames) }

    /**
     * Set a custom [ClassDataProvider].
     */
    fun classDataProvider(classDataProvider: ClassDataProvider) =
      apply { this.classDataProvider = classDataProvider }

    /**
     * Add extra attributes to the `manifest` file.
     * As possible attribute name please see [ManifestPluginDescriptorFinder].
     */
    fun manifestAttributes(manifestAttributes: Map<String, String>) =
      apply { this.manifestAttributes.putAll(manifestAttributes) }

    /**
     * Add extra attribute to the `manifest` file.
     * As possible attribute name please see [ManifestPluginDescriptorFinder].
     */
    fun manifestAttribute(name: String, value: String) =
      apply { this.manifestAttributes[name] = value }

    /**
     * Build the JAR.
     */
    fun build(): PluginJar {
      val manifest = createManifest()
      FileOutputStream(path.toFile()).use { outputStream ->
        val jarOutputStream = JarOutputStream(outputStream, manifest)
        if (extensions.isNotEmpty()) {
          // add extensions.idx
          val jarEntry = JarEntry("META-INF/extensions.idx")
          jarOutputStream.putNextEntry(jarEntry)
          jarOutputStream.write(extensionsAsByteArray())
          jarOutputStream.closeEntry()
          // add extensions classes
          for (extension in extensions) {
            val extensionPath = extension.replace('.', '/') + ".class"
            val classEntry = JarEntry(extensionPath)
            jarOutputStream.putNextEntry(classEntry)
            jarOutputStream.write(classDataProvider.getClassData(extension))
            jarOutputStream.closeEntry()
          }
        }
        jarOutputStream.close()
      }

      return PluginJar(this)
    }

    private fun createManifest(): Manifest {
      val map = mutableMapOf<String, String>()
      map[ManifestPluginDescriptorFinder.PLUGIN_ID] = pluginId
      map[ManifestPluginDescriptorFinder.PLUGIN_VERSION] = pluginVersion
      map[ManifestPluginDescriptorFinder.PLUGIN_CLASS] = pluginClass
      map.putAll(manifestAttributes)

      return createManifest(map)
    }

    private fun extensionsAsByteArray(): ByteArray {
      ByteArrayOutputStream().use { outputStream ->
        val writer = PrintWriter(outputStream)
        for (extension in extensions) {
          writer.println(extension)
        }
        writer.flush()

        return outputStream.toByteArray()
      }
    }
  }

  companion object {
    /**
     * Create a PF4J plugin JAR manifest.
     */
    fun createManifest(map: Map<String, String>): Manifest {
      val manifest = Manifest()
      val attributes = manifest.mainAttributes
      attributes[Attributes.Name.MANIFEST_VERSION] = "1.0.0"
      for ((key, value) in map) {
        attributes[Attributes.Name(key)] = value
      }
      return manifest
    }
  }

  init {
    path = builder.path
    pluginId = builder.pluginId
    pluginClass = builder.pluginClass
    pluginVersion = builder.pluginVersion
  }
}
