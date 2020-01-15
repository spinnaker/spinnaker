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
package com.netflix.spinnaker.kork.plugins.bundle

import com.netflix.spinnaker.kork.exceptions.IntegrationException
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expect
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isTrue
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginBundleExtractorTest : JUnit5Minutests {

  fun tests() = rootContext<PluginBundleExtractor> {
    fixture { PluginBundleExtractor() }

    before {
      // Creates 3 zips: Two service plugin zips, and then a bundle zip containing the service plugin zips
      val bundleDir = ZipBuilder.workspace.resolve("bundleSrc").also {
        try {
          Files.createDirectory(it)
        } catch (e: FileAlreadyExistsException) {
          // Do nothing
        }
      }

      ZipBuilder(javaClass.getResource("/bundle/deck").toPath(), "deck.zip", bundleDir).build()
      ZipBuilder(javaClass.getResource("/bundle/orca").toPath(), "orca.zip", bundleDir).build()
      ZipBuilder(bundleDir, "bundle.zip", ZipBuilder.workspace).build()
    }

    after {
      ZipBuilder.workspace.toFile().listFiles()?.forEach { it.deleteRecursively() }
    }

    context("bundle extraction") {
      test("extracts bundles") {
        extractBundle(ZipBuilder.workspace.resolve("bundle.zip"))

        expect {
          that(ZipBuilder.workspace.resolve("bundle/deck.zip").toFile()).get { exists() }.isTrue()
          that(ZipBuilder.workspace.resolve("bundle/orca.zip").toFile()).get { exists() }.isTrue()
        }
      }

      test("service is extracted from a bundle") {
        extractService(ZipBuilder.workspace.resolve("bundle.zip"), "deck")

        expectThat(ZipBuilder.workspace.resolve("bundle/deck/index.js").toFile()).get { exists() }.isTrue()
      }

      test("service can be extracted from already extracted bundle") {
        extractService(
          extractBundle(ZipBuilder.workspace.resolve("bundle.zip")),
          "deck"
        )

        expectThat(ZipBuilder.workspace.resolve("bundle/deck/index.js").toFile()).get { exists() }.isTrue()
      }

      test("throws if bundle is missing expected service plugin") {
        expectThrows<IntegrationException> {
          extractService(ZipBuilder.workspace.resolve("bundle.zip"), "clouddriver")
        }
      }
    }

    test("backwards compatible with unbundled plugins") {
      extractService(ZipBuilder.workspace.resolve("bundleSrc").resolve("deck.zip"), "deck")

      expectThat(ZipBuilder.workspace.resolve("bundleSrc/deck/index.js").toFile()).get { exists() }.isTrue()
    }
  }

  private class ZipBuilder(
    private val sourceRootPath: Path,
    private val zipFilename: String,
    private val destination: Path
  ) {

    private val fileList: MutableList<String> = mutableListOf()

    fun build() {
      generateFileList(sourceRootPath.toFile())

      try {
      } catch (e: FileAlreadyExistsException) {
        // Do nothing
      }

      FileOutputStream(destination.resolve(zipFilename).toString()).use { fos ->
        ZipOutputStream(fos).use { zos ->
          fileList.forEach { file ->
            val ze = ZipEntry(Paths.get(file).fileName.toString())
            zos.putNextEntry(ze)

            FileInputStream(sourceRootPath.resolve(file).toString()).use { input ->
              zos.write(input.readBytes())
            }
          }

          zos.closeEntry()
        }
      }
    }

    fun generateFileList(node: File) {
      if (node.isFile) {
        fileList.add(generateZipEntry(node.toString()))
      }
      if (node.isDirectory) {
        node.list()?.forEach {
          generateFileList(File(node, it))
        }
      }
    }

    private fun generateZipEntry(filePath: String): String {
      return filePath.substring(sourceRootPath.toString().length + 1, filePath.length)
    }

    companion object {
      val workspace = Files.createTempDirectory("plugins")
    }
  }

  private fun URL.toPath(): Path =
    File(this.toURI()).toPath()
}
