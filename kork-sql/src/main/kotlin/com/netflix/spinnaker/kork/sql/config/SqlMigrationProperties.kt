/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.sql.config

/**
 * Defines the configuration properties for connecting to a SQL database for schema migration purposes.
 *
 * @param jdbcUrl The JDBC URL to use to connect to the database
 * @param user The user to connect to the database with
 * @param password The password to authenticate the [user]
 * @param driver The JDBC driver name
 * @param additionalChangeLogs A list of additional change log paths. This is useful for libraries and extensions.
 */
data class SqlMigrationProperties(
  var jdbcUrl: String? = null,
  var user: String? = null,
  var password: String? = null,
  var driver: String? = null,
  var additionalChangeLogs: List<String> = mutableListOf()
)
