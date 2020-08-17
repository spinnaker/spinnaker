/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.cats.sql.cache

import com.google.common.hash.Hashing
import com.netflix.spinnaker.config.SqlConstraints
import com.netflix.spinnaker.kork.annotations.VisibleForTesting

/**
 * Provides utility methods for clouddriver's SQL naming conventions.
 */
class SqlNames(
  private val tableNamespace: String? = null,
  private val sqlConstraints: SqlConstraints = SqlConstraints()
) {

  /**
   * Get the resource table name for a given agent type.
   */
  fun resourceTableName(type: String): String =
    checkTableName("cats_v${schemaVersion}_", sanitizeType(type), "")

  /**
   * Get the relationship table name for a given agent type.
   */
  fun relTableName(type: String): String =
    checkTableName("cats_v${schemaVersion}_", sanitizeType(type), "_rel")

  private fun sanitizeType(type: String): String {
    return type.replace(typeSanitization, "_")
  }

  /**
   * Computes the actual name of the table less than MAX_TABLE_NAME_LENGTH characters long.
   * It always keeps prefix with tableNamespace but can shorten name and suffix in that order.
   * @return computed table name
   */
  @VisibleForTesting
  internal fun checkTableName(prefix: String, name: String, suffix: String): String {
    var base = prefix
    if (tableNamespace != null) {
      base = "${prefix + tableNamespace}_"
    }

    // Optimistic and most frequent case
    val tableName = base + name + suffix
    if (tableName.length < sqlConstraints.maxTableNameLength) {
      return tableName
    }

    // Hash the name and keep the suffix
    val hash = Hashing.murmur3_128().hashBytes((name + suffix).toByteArray()).toString().substring(0..15)
    val available = sqlConstraints.maxTableNameLength - base.length - suffix.length - hash.length - 1
    if (available >= 0) {
      return base + name.substring(0..available) + hash + suffix
    }

    // Remove suffix
    if (available + suffix.length >= 0) {
      return base + name.substring(0..(available + suffix.length)) + hash
    }
    throw IllegalArgumentException("property sql.table-namespace $tableNamespace is too long")
  }

  companion object {
    private val schemaVersion = SqlSchemaVersion.current()
    private val typeSanitization =
      """[^A-Za-z0-9_]""".toRegex()
  }
}
