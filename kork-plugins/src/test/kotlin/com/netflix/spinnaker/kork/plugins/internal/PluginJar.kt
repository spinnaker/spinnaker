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

import org.pf4j.ManifestPluginDescriptorFinder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class PluginJar(
  val path: Path,
  val pluginId: String,
  val pluginClass: String,
  val pluginVersion: String
) {

  val file: File = path.toFile()

  class Builder(private val path: Path, private val pluginId: String) {

    private lateinit var pluginClass: String
    private lateinit var pluginVersion: String
    private val manifestAttributes: MutableMap<String, String> = mutableMapOf()
    private val extensions: MutableList<String> = mutableListOf()
    private var classDataProvider: ClassDataProvider = DefaultClassDataProvider()

    fun pluginClass(pluginClass: String): Builder {
      this.pluginClass = pluginClass
      return this
    }

    fun pluginVersion(pluginVersion: String): Builder {
      this.pluginVersion = pluginVersion
      return this
    }

    /**
     * Add extra attributes to the `manifest` file.
     * As possible attribute name please see [ManifestPluginDescriptorFinder].
     */
    fun manifestAttributes(manifestAttributes: Map<String, String>): Builder {
      this.manifestAttributes.plus(manifestAttributes)
      return this
    }

    /**
     * Add extra attribute to the `manifest` file.
     * As possible attribute name please see [ManifestPluginDescriptorFinder].
     */
    fun manifestAttribute(name: String, value: String): Builder {
      manifestAttributes[name] = value

      return this
    }

    fun extension(extensionClassName: String): Builder {
      extensions.add(extensionClassName)
      return this
    }

    fun classDataProvider(classDataProvider: ClassDataProvider): Builder {
      this.classDataProvider = classDataProvider
      return this
    }

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

      return PluginJar(path, pluginId, pluginClass, pluginVersion)
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
}
