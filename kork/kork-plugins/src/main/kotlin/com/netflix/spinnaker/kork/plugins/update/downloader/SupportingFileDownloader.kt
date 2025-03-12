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

import java.net.URL
import org.pf4j.update.FileDownloader

/**
 * A [FileDownloader] that can inspect a URL prior to downloading the provided file.
 *
 * Custom FileDownloaders should use this interface if there is a chance that an [org.pf4j.update.UpdateRepository]
 * may need to download plugin binaries from different locations with varying download strategies. These
 * FileDownloaders are used automatically in the [CompositeFileDownloader].
 */
interface SupportingFileDownloader : FileDownloader {

  /**
   * @return True if [url] is supported by this [FileDownloader].
   */
  fun supports(url: URL): Boolean
}
