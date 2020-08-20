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

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.DiagnosticCollector
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider

/**
 * Generates [GeneratedTestPlugin]s.
 */
class TestPluginGenerator(
  private val testPlugin: GeneratedTestPlugin,
  val rootPath: Path
) {

  val pluginPath = Files.createTempDirectory(rootPath, testPlugin.name).recursivelyDeleteOnExit()

  init {
    rootPath.recursivelyDeleteOnExit()
  }

  fun generate() {
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

  private fun generateSources(): Pair<Path, Array<File>> {
    val tempDir = Files.createTempDirectory("plugincodegen").recursivelyDeleteOnExit()
    val packageDir = tempDir.resolve(testPlugin.packageName.replace('.', '/'))
    packageDir.toFile().mkdirs()

    return Pair(
      tempDir,
      testPlugin.sourceFiles()
        .map { source ->
          packageDir.resolve("${source.simpleName}.java").toFile().also {
            it.writeText(substituteVariables(source.simpleName, source.contents))
          }
        }
        .toTypedArray()
    )
  }

  /**
   * Substitutes variable placeholders with their associated values.
   */
  private fun substituteVariables(simpleName: String, body: String): String {
    var result = body
    mapOf(
      "pluginName" to testPlugin.name,
      "simpleName" to simpleName,
      "basePackageName" to testPlugin.packageName
    ).forEach { (t, u) ->
      result = result.replace("{{$t}}", u)
    }
    return result
  }

  private fun preparePluginDestination(): File {
    pluginPath.toFile().deleteRecursively()
    val classOutput = pluginPath.resolve("classes").toFile()
    classOutput.mkdirs()
    return classOutput
  }

  private fun writePluginProperties(): Unit =
    pluginPath.resolve("plugin.properties").toFile().writeText(testPlugin.properties())

  private fun setupCompiler(classesDir: File, generated: Pair<Path, Array<File>>): CompilerSetup {
    val compiler = ToolProvider.getSystemJavaCompiler()
    val diag = DiagnosticCollector<JavaFileObject>()
    val sfm = compiler.getStandardFileManager(diag, null, Charsets.UTF_8)
    sfm.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
    sfm.setLocation(StandardLocation.SOURCE_PATH, listOf(generated.first.toFile()))
    sfm.setLocation(StandardLocation.CLASS_PATH, systemClasspath())
    val javaFiles = sfm.getJavaFileObjects(*generated.second)
    return CompilerSetup(
      task = compiler.getTask(null, sfm, diag, null, null, javaFiles),
      diagnostics = diag
    )
  }

  private fun systemClasspath(): Iterable<File> =
    System.getProperty("java.class.path").split(System.getProperty("path.separator")).map { File(it) }

  /**
   * Recursively deletes a path on exit.
   */
  private fun Path.recursivelyDeleteOnExit(): Path =
    also { toFile().recursiveDeleteOnExit() }

  /**
   * Recursively deletes a file on exit.
   */
  private fun File.recursiveDeleteOnExit() {
    deleteOnExit()
    listFiles()?.forEach {
      if (it.isDirectory) {
        it.recursiveDeleteOnExit()
      } else {
        it.deleteOnExit()
      }
    }
  }

  private data class CompilerSetup(
    val task: JavaCompiler.CompilationTask,
    val diagnostics: DiagnosticCollector<JavaFileObject>
  )
}
