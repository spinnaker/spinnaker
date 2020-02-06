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

package com.netflix.spinnaker.kork.plugins.testplugin

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.DiagnosticCollector
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider

/**
 * Generates new java source and compiles it to dynamically create a Plugin that is not in the current classpath.
 *
 * The generated Plugin is an expanded directory type plugin with a properties file plugin descriptor. It
 * has an Extension implementing {@code TestExtension}
 *
 * @see com.netflix.spinnaker.kork.plugins.testplugin.api.TestExtension
 *
 */
class TestPluginBuilder(
  /**
   * The directory in which to create a plugin.
   */
  val pluginPath: Path,

  /**
   * The package name for the generated Plugin and Extension.
   */
  val packageName: String = "com.netflix.spinnaker.kork.plugins.testplugin.generated",

  /**
   * The name of the generated plugin and extension.
   * The plugin will be ${name}TestPlugin and the Extension ${name}TestExtension.
   */
  val name: String = "Generated",

  /**
   * The version of the generated plugin.
   */
  val version: String = "0.0.1"
) {

  /**
   * The canonical Plugin class name.
   */
  val canonicalPluginClass = "$packageName.${name}TestPlugin"

  /**
   * The canonical Extension class name.
   */
  val canonicalExtensionClass = "$packageName.${name}TestExtension"

  /**
   * The fully resolved plugin ID.
   */
  val pluginId = "spinnaker.${name.toLowerCase()}testplugin"

  fun build() {
    val generated = generateSources()
    val classesDir = preparePluginDestination()
    writePluginProperties()
    val compilerSetup = setupCompiler(classesDir, generated)
    if (!compilerSetup.task.call()) {
      val message = compilerSetup.diagnostics.diagnostics.joinToString(separator = System.lineSeparator()) {
        "${it.kind} in ${it.source} on line ${it.lineNumber} at ${it.columnNumber}: ${it.getMessage(null)}"
      }
      throw IllegalStateException("generation failed: ${System.lineSeparator()}$message")
    }
  }

  private data class GenerateResult(
    val rootDir: File,
    val pluginFile: File,
    val extensionFile: File
  )

  private fun generateSources(): GenerateResult {
    val tempDir = Files.createTempDirectory("plugincodegen")
    val packageDir = tempDir.resolve(packageName.replace('.', '/'))
    packageDir.toFile().mkdirs()
    val pluginFile = packageDir.resolve("${name}TestPlugin.java")
    pluginFile.toFile().writeText(pluginSrc)
    val extensionFile = packageDir.resolve("${name}TestExtension.java")
    extensionFile.toFile().writeText(extensionSrc)

    fun cleanupTemp(dir: File) {
      dir.deleteOnExit()
      dir.listFiles()?.forEach {
        if (it.isDirectory) {
          cleanupTemp(it)
        } else {
          it.deleteOnExit()
        }
      }
    }

    cleanupTemp(tempDir.toFile())
    return GenerateResult(
      rootDir = tempDir.toFile(),
      pluginFile = pluginFile.toFile(),
      extensionFile = extensionFile.toFile())
  }

  private fun systemClasspath(): Iterable<File> =
    System.getProperty("java.class.path").split(System.getProperty("path.separator")).map { File(it) }

  private fun preparePluginDestination(): File {
    pluginPath.toFile().deleteRecursively()
    val classOutput = pluginPath.resolve("classes").toFile()
    classOutput.mkdirs()
    return classOutput
  }

  private fun writePluginProperties(): Unit =
    pluginPath.resolve("plugin.properties").toFile().writeText(pluginProperties)

  private data class CompilerSetup(
    val task: JavaCompiler.CompilationTask,
    val diagnostics: DiagnosticCollector<JavaFileObject>
  )

  private fun setupCompiler(classesDir: File, generated: GenerateResult): CompilerSetup {
    val compiler = ToolProvider.getSystemJavaCompiler()
    val diag = DiagnosticCollector<JavaFileObject>()
    val sfm = compiler.getStandardFileManager(diag, null, Charsets.UTF_8)
    sfm.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
    sfm.setLocation(StandardLocation.SOURCE_PATH, listOf(generated.rootDir))
    sfm.setLocation(StandardLocation.CLASS_PATH, systemClasspath())
    val javaFiles = sfm.getJavaFileObjects(generated.pluginFile, generated.extensionFile)
    return CompilerSetup(
      task = compiler.getTask(null, sfm, diag, null, null, javaFiles),
      diagnostics = diag)
  }

  private val pluginSrc =
    """
    package $packageName;

    import org.pf4j.Plugin;
    import org.pf4j.PluginWrapper;

    public class ${name}TestPlugin extends Plugin {
      public ${name}TestPlugin(PluginWrapper wrapper) {
        super(wrapper);
      }
    }
    """.trimIndent()

  private val extensionSrc =
    """
    package $packageName;

    import com.netflix.spinnaker.kork.plugins.api.SpinnakerExtension;
    import com.netflix.spinnaker.kork.plugins.testplugin.api.TestExtension;
    import org.pf4j.Extension;

    @Extension
    @SpinnakerExtension(id = "spinnaker.${name.toLowerCase()}-test-extension")
    public class ${name}TestExtension implements TestExtension {
      @Override
      public String getTestValue() {
        return getClass().getSimpleName();
      }
    }
    """.trimIndent()

  private val pluginProperties =
    """
    plugin.id=$pluginId
    plugin.description=A generated TestPlugin named $name
    plugin.class=$canonicalPluginClass
    plugin.version=$version
    plugin.provider=Spinnaker
    plugin.dependencies=
    plugin.requires=*
    plugin.license=Apache 2.0
    plugin.unsafe=false
    """.trimIndent()
}
