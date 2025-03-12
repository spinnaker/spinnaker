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

package com.netflix.spinnaker.kork.plugins

import com.github.zafarkhaja.semver.Version
import org.pf4j.VersionManager
import org.pf4j.util.StringUtils
import org.slf4j.LoggerFactory

/**
 * Since plugins may require multiple services, this class is necessary to ensure we are making the
 * constraint check against the correct service.
 */
class SpinnakerServiceVersionManager(
  private val serviceName: String
) : VersionManager {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun checkVersionConstraint(version: String, requires: String): Boolean {

    if (requires.isEmpty()) {
      log.warn("Loading plugin with empty Plugin-Requires attribute!")
      return true
    }

    val requirements =
      VersionRequirementsParser
        .parseAll(requires)
        .find { it.service.equals(serviceName, ignoreCase = true) }

    if (requirements != null) {
      return StringUtils.isNullOrEmpty(requirements.constraint) || Version.valueOf(version).satisfies(requirements.constraint)
    }

    return false
  }

  override fun compareVersions(v1: String, v2: String): Int {
    return Version.valueOf(v1).compareTo(Version.valueOf(v2))
  }
}
