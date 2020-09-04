/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.kork.plugins.testplugin

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import com.netflix.spinnaker.kork.plugins.finders.SpinnakerPropertiesPluginDescriptorFinder
import java.lang.IllegalStateException
import java.nio.file.Files
import java.nio.file.Path

/**
 * A flexible runtime-generated test plugin.
 *
 * Should be built with [testPlugin].
 */
class GeneratedTestPlugin {

  /**
   * The plugin name. This will be used as part of some generated class names, as well as in the plugin ID itself.
   */
  var name: String = "Generated"
    set(value) {
      field = value.capitalize()
    }

  /**
   * The root package name the plugin classes will live.
   */
  var packageName: String = "com.netflix.spinnaker.kork.plugins.testplugin.generated"

  /**
   * The plugin version.
   */
  var version: String = "0.0.1"

  /**
   * The plugin ID of the generated plugin.
   */
  var pluginId: String = "spinnaker.${name.toLowerCase()}-testplugin"
  internal var sources: MutableList<SourceFile> = mutableListOf()
  internal var pluginClass: SourceFile? = null

  private var generated: Boolean = false

  /**
   * Set the plugin class contents.
   */
  fun pluginClass(contents: String) {
    pluginClass = SourceFile(name, contents)
  }

  /**
   * Add a new source file to the plugin.
   */
  fun sourceFile(simpleName: String, contents: String) {
    sources.add(SourceFile(simpleName, contents))
  }

  /**
   * Returns a list of all [SourceFile]s in the plugin.
   */
  fun sourceFiles(): List<SourceFile> =
    listOf(pluginClass())
      .plus(sources)

  internal fun pluginClass(): SourceFile {
    return pluginClass ?: DefaultPluginClassSourceFile(name, packageName)
  }

  /**
   * Returns a rendered properties file for the plugin.
   */
  fun properties(): String {
    return """
      plugin.id=$pluginId
      plugin.description=A generated TestPlugin named $name
      plugin.class=${canonicalPluginClass()}
      plugin.version=$version
      plugin.provider=Spinnaker
      plugin.dependencies=
      plugin.requires=*
      plugin.license=Apache 2.0
      plugin.unsafe=false
    """.trimIndent()
  }

  /**
   * Returns the canonical class name given a relative package and class name value.
   */
  fun canonicalClass(className: String): String =
    "$packageName.$className"

  /**
   * Returns the canonical plugin class name.
   */
  fun canonicalPluginClass(): String =
    canonicalClass(pluginClass().simpleName)

  /**
   * Generates the plugin into the given [rootPath].
   */
  fun generate(rootPath: Path? = null): GenerateResult {
    if (generated) {
      throw IllegalStateException("A test plugin instance cannot be generated more than once")
    }

    return TestPluginGenerator(this, rootPath ?: Files.createTempDirectory("generatedplugin")).let {
      it.generate()
      generated = true

      GenerateResult(
        rootPath = it.rootPath,
        pluginPath = it.pluginPath,
        descriptor = SpinnakerPropertiesPluginDescriptorFinder().find(it.pluginPath) as SpinnakerPluginDescriptor,
        plugin = this
      )
    }
  }

  /**
   * @param rootPath The plugins root directory; generated plugins are subdirectories under this path.
   * @param pluginPath The path to the generated plugin within the plugins directory.
   * @param descriptor The plugin descriptor for this generated plugin.
   * @param plugin Self-reference to this [GeneratedTestPlugin].
   */
  class GenerateResult(
    val rootPath: Path,
    val pluginPath: Path,
    val descriptor: SpinnakerPluginDescriptor,
    val plugin: GeneratedTestPlugin
  )
}

/**
 * A single Java source file.
 *
 * @param simpleName The simple class name of the source file
 * @param contents The contents of the source file
 */
open class SourceFile(var simpleName: String, var contents: String)

/**
 * The default [Plugin] source file.
 *
 * You will usually not have to change this.
 */
class DefaultPluginClassSourceFile(
  pluginName: String,
  packageName: String
) : SourceFile(
  pluginName,
  """
    package $packageName;

    import org.pf4j.Plugin;
    import org.pf4j.PluginWrapper;

    public class {{simpleName}} extends Plugin {
      public {{simpleName}}(PluginWrapper wrapper) {
        super(wrapper);
      }
    }
  """.trimIndent()
)
