/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.sql.routing

/**
 * Convenience method for use with jOOQ queries, defining the connection pool to use by name. If the requested
 * connection pool does not exist, the configured default connection pool will be used.
 *
 * ```
 * val result = withPool("myPool") {
 *   jooq.select(...).fetchOne()
 * }
 * ```
 *
 * @param name The name of the connection pool
 * @param callback The code to execute with the provided connection pool targeted
 */
inline fun <T> withPool(name: String, callback: () -> T): T {
  NamedDatabaseContextHolder.set(name)
  try {
    return callback()
  } finally {
    NamedDatabaseContextHolder.clear()
  }
}
