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

import com.github.zafarkhaja.semver.ParseException
import com.github.zafarkhaja.semver.Version
import com.netflix.spinnaker.kork.exceptions.UserException
import java.util.regex.Pattern

/**
 * Provides utility methods for parsing version requirements values from and to [VersionRequirements].
 *
 * Version requirements are in the format of "{service}{constraint}", where:
 *
 * - `service` is the service name that is supported by a plugin
 * - `constraint` is a semVer expression to be constrained ( >=1.5.0 , >=1.0.0 & <2.0.0)
 *
 */
object VersionRequirementsParser {

  private val SUPPORTS_PATTERN = Pattern.compile(
    "^(?<service>[\\w\\-]+)(?<constraint>.*[><=]{1,2}[0-9]+\\.[0-9]+\\.[0-9]+.*)$"
  )
  private val CONSTRAINT_VALIDATOR = Version.valueOf("0.0.0")

  private const val SUPPORTS_PATTERN_SERVICE_GROUP = "service"
  private const val SUPPORTS_PATTERN_CONSTRAINT_GROUP = "constraint"

  /**
   * Parse a single version.
   */
  fun parse(version: String): VersionRequirements {
    return SUPPORTS_PATTERN.matcher(version)
      .also {
        if (!it.matches()) {
          throw InvalidPluginVersionRequirementException(version)
        }
        // we use semver to validate that the constraint is valid.
        try {
          CONSTRAINT_VALIDATOR.satisfies(it.group(SUPPORTS_PATTERN_CONSTRAINT_GROUP))
        } catch (e: ParseException) {
          throw InvalidPluginVersionRequirementException(version)
        }
      }
      .let {
        VersionRequirements(
          service = it.group(SUPPORTS_PATTERN_SERVICE_GROUP),
          constraint = it.group(SUPPORTS_PATTERN_CONSTRAINT_GROUP)
        )
      }
  }

  /**
   * Parse a list of comma-delimited versions.
   */
  fun parseAll(version: String): List<VersionRequirements> =
    version.split(',').map { parse(it.trim()) }

  /**
   * Convert a list of [VersionRequirements] into a string.
   */
  fun stringify(requirements: List<VersionRequirements>): String =
    requirements.joinToString(",") { it.toString() }

  /**
   * Version constraint requirements for a plugin release.
   *
   * @param service The service that this requirement is for
   * @param constraint The SemVer constraint expression
   */
  data class VersionRequirements(
    val service: String,
    val constraint: String
  ) {
    override fun toString(): String = "$service$constraint"
  }

  /**
   * Thrown when a given version requirement is invalid.
   */
  class InvalidPluginVersionRequirementException(version: String) : UserException(
    "The provided version requirement '$version' is not valid: It must conform a valid semantic version expression"
  )
}
