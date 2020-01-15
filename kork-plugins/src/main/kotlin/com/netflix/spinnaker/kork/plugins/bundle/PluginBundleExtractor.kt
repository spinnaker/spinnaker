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
import org.pf4j.util.FileUtils
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Provides extraction capabilities for plugin bundles.
 *
 * Plugin Bundles are a ZIP files containing service-specific plugin ZIP files. When the plugin bundle is downloaded,
 * we first need to extract the bundle, then locate the service plugin ZIP and extract that. Plugin bundles are based
 * on naming convention, so we can assume that a service ZIP will always be "{service}.zip".
 *
 * Individual service plugins will be as what PF4J would normally expect.
 */
class PluginBundleExtractor {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * Extract the bundle. This does not unpack any of the underlying plugin zips.
   */
  fun extractBundle(bundlePath: Path): Path {
    return FileUtils.expandIfZip(bundlePath)
  }

  /**
   * Extract a specific service from a bundle.
   */
  fun extractService(bundlePath: Path, service: String): Path {
    val extractedPath = extractBundle(bundlePath)
    if (!looksLikeBundle(extractedPath)) {
      log.debug("Plugin path does not appear to be a bundle, using as-is: {}", bundlePath)
      return extractedPath
    }

    val servicePluginZipPath = extractedPath.resolve("$service.zip")
    if (servicePluginZipPath.toFile().exists()) {
      return FileUtils.expandIfZip(servicePluginZipPath)
    }

    // If thrown, this is an indicator that either: A) There's a bug in the plugin framework resolving which plugin
    // bundles should actually be downloaded, or B) The plugin author incorrectly identified this [service] as one
    // that the plugin extends (via the PluginInfo `requires` list).
    throw IntegrationException("Downloaded plugin bundle does not have plugin for service '$service'")
  }

  /**
   * Inspects the initially extracted [pluginPath] and looks for nested zip files. If nested zip files cannot be
   * found, it's assumed that the plugin path provided was not a plugin bundle.
   */
  private fun looksLikeBundle(pluginPath: Path): Boolean {
    return pluginPath.toFile().listFiles()?.any { it.name.endsWith(".zip") } ?: false
  }
}
