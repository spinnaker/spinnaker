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
package com.netflix.spinnaker.kork.plugins.update.downloader.internal

import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.update.downloader.ProcessFileDownloader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Default [ProcessFileDownloader.ProcessRunner] implementation.
 */
internal class DefaultProcessRunner : ProcessFileDownloader.ProcessRunner {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun completeOrTimeout(processBuilder: ProcessBuilder): String {
    val process = processBuilder.start()
    val exitCode = process.waitFor()

    log.emitProcessLogs(process)

    if (exitCode != 0) {
      throw ProcessFatalException(exitCode)
    }

    return process.inputStream.bufferedReader().use(BufferedReader::readText)
  }

  private fun Logger.emitProcessLogs(process: Process) {
    BufferedReader(InputStreamReader(process.errorStream))
      .lines()
      .forEach { l: String? ->
        debug(l)
      }
  }

  internal inner class ProcessFatalException(exitCode: Int) :
    IntegrationException("download process exited with fatal code: $exitCode")
}
