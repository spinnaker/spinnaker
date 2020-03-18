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
package com.netflix.spinnaker.kork.plugins.update.downloader

import com.netflix.spinnaker.kork.annotations.VisibleForTesting
import com.netflix.spinnaker.kork.plugins.config.Configurable
import com.netflix.spinnaker.kork.plugins.update.downloader.internal.DefaultProcessRunner
import java.io.FileNotFoundException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.pf4j.update.FileDownloader
import org.slf4j.LoggerFactory

/**
 * Runs a local process to handle the downloading of a plugin binary.
 */
@Configurable(ProcessFileDownloaderConfig::class)
class ProcessFileDownloader(
  @VisibleForTesting internal val config: ProcessFileDownloaderConfig,
  private val processRunner: ProcessRunner
) : FileDownloader {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * For satisfying the [Configurable] functionality.
   */
  constructor(config: ProcessFileDownloaderConfig) : this(config, DefaultProcessRunner())

  override fun downloadFile(fileUrl: URL): Path {
    log.debug("Downloading plugin binary: $fileUrl")

    val builder = ProcessBuilder().apply {
      directory(Files.createTempDirectory("plugin-downloads").toFile())
      environment().putAll(config.env)
      command(*config.command.split(" ").toTypedArray())
    }

    val path = Paths.get(processRunner.completeOrTimeout(builder))

    log.debug("Received downloaded plugin path: $path (from $fileUrl)")

    if (!path.toFile().exists()) {
      throw FileNotFoundException("The downloaded file could not be found on the file system")
    }

    return path
  }

  /**
   * Allows for switching out the actual process execution logic. This is primarily for
   * the purposes of mocking process execution during unit tests.
   */
  interface ProcessRunner {
    fun completeOrTimeout(processBuilder: ProcessBuilder): String
  }
}
