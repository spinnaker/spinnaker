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
package com.netflix.spinnaker.kork.plugins.update.repository

import java.net.URL
import org.pf4j.update.DefaultUpdateRepository
import org.pf4j.update.FileDownloader
import org.pf4j.update.FileVerifier
import org.pf4j.update.SimpleFileDownloader
import org.pf4j.update.verifier.CompoundVerifier

/**
 * PF4J forces extensible for configurability. This class just makes it a little more reasonable to bring your own
 * file downloader and verifier to a particular repository.
 */
class ConfigurableUpdateRepository(
  id: String,
  url: URL,
  private val downloader: FileDownloader = SimpleFileDownloader(),
  private val verifier: FileVerifier = CompoundVerifier()
) : DefaultUpdateRepository(id, url) {

  override fun getFileDownloader(): FileDownloader = downloader
  override fun getFileVerifier(): FileVerifier = verifier
}
