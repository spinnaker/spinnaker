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

import com.netflix.spinnaker.kork.exceptions.UserException
import java.util.regex.Pattern

/**
 * Provides utility methods for parsing version requirements values from and to [VersionRequirements].
 *
 * Version requirements are in the format of "{service}{operator}{version}", where:
 *
 * - `service` is the service name that is supported by a plugin
 * - `operator` is a version constraint operator (`>`, `<`, `>=`, `<=`)
 * - `version` is the service version that is being constrained
 */
object VersionRequirementsParser {

  private val SUPPORTS_PATTERN = Pattern.compile(
    "^(?<service>[\\w\\-]+)(?<operator>[><=]{1,2})(?<version>[0-9]+\\.[0-9]+\\.[0-9]+)$")

  private const val SUPPORTS_PATTERN_SERVICE_GROUP = "service"
  private const val SUPPORTS_PATTERN_OPERATOR_GROUP = "operator"
  private const val SUPPORTS_PATTERN_VERSION_GROUP = "version"

  /**
   * Parse a single version.
   */
  fun parse(version: String): VersionRequirements {
    return SUPPORTS_PATTERN.matcher(version)
      .also {
        if (!it.matches()) {
          throw InvalidPluginVersionRequirementException(version)
        }
      }
      .let {
        VersionRequirements(
          service = it.group(SUPPORTS_PATTERN_SERVICE_GROUP),
          operator = VersionRequirementOperator.from(it.group(SUPPORTS_PATTERN_OPERATOR_GROUP)),
          version = it.group(SUPPORTS_PATTERN_VERSION_GROUP)
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

  enum class VersionRequirementOperator(val symbol: String) {
    GT(">"),
    LT("<"),
    GT_EQ(">="),
    LT_EQ("<=");

    companion object {
      fun from(symbol: String): VersionRequirementOperator =
        values().find { it.symbol == symbol }
          ?: throw IllegalVersionRequirementsOperator(symbol, values().map { it.symbol }.joinToString { "'$it'" })
    }
  }

  data class VersionRequirements(
    val service: String,
    val operator: VersionRequirementOperator,
    val version: String
  ) {
    override fun toString(): String = "$service${operator.symbol}$version"
  }

  class InvalidPluginVersionRequirementException(version: String) : UserException(
    "The provided version requirement '$version' is not valid: It must conform to '$SUPPORTS_PATTERN'"
  )

  class IllegalVersionRequirementsOperator(symbol: String, availableOperators: String) : UserException(
    "Illegal version requirement operator '$symbol': Must be one of $availableOperators"
  )
}
