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

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.sql.Connection
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import kotlin.reflect.KClass

/**
 * The entrypoint configuration properties for SQL integrations.
 *
 * This configuration supports multiple, named connection pools, as well as dedicated configurations for
 * performing database migrations.
 *
 * @param migration The primary migration configuration
 * @param secondaryMigration Migration configuration for the secondary database, if one is available
 * @param connectionPools All non-migration connection pools for the application
 * @param retries Default, global retry configuration across connection pools
 * @param setTransactionIsolation if true, set the transaction isolation level on each database connection.  Note that the the jdbc driver may have a setting (e.g. mysql-connector-java has alwaysSendSetIsolation, see https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-configuration-properties.html) that influences behavior here.
 * @param transactionIsolation the transaction isolation level to set, required if setTransactionIsolation is true.
 *   See e.g. https://docs.oracle.com/en/java/javase/11/docs/api/constant-values.html#java.sql.Connection.TRANSACTION_NONE.
 * @param connectionPool Deprecated. Use [connectionPools] instead.
 */
@SqlProperties.SpinValidated
@ConfigurationProperties("sql")
data class SqlProperties(
  var migration: SqlMigrationProperties = SqlMigrationProperties(),
  var secondaryMigration: SqlMigrationProperties = SqlMigrationProperties(),
  var connectionPools: MutableMap<String, ConnectionPoolProperties> = mutableMapOf(),
  var retries: SqlRetryProperties = SqlRetryProperties(),
  var setTransactionIsolation: Boolean = true,
  var transactionIsolation : Int? = Connection.TRANSACTION_READ_COMMITTED,

  @Deprecated("use named connection pools instead")
  var connectionPool: ConnectionPoolProperties? = null
) {

  /**
   * Convenience method for accessing all connection pool properties, backwards-compatible with deprecated
   * [connectionPool] configuration.
   */
  fun getDefaultConnectionPoolProperties(): ConnectionPoolProperties {
    if (connectionPools.isEmpty()) {
      if (connectionPool == null) {
        throw MisconfiguredConnectionPoolsException.NEITHER_PRESENT
      }
      return connectionPool!!
    }
    return if (connectionPools.size == 1) {
      connectionPools.values.first()
    } else {
      connectionPools.values.first { it.default }
    }
  }

  /**
   * Validation annotation for SqlProperties
   */
  @Validated
  @Constraint(validatedBy = [Validator::class])
  @Target(AnnotationTarget.CLASS)
  annotation class SpinValidated(
    /**
     * default error message
     */
    val message: String = "Invalid sql configuration",

    /**
     * to customize the targeted groups
     */
    val groups: Array<KClass<out Any>> = [],

    /**
     * for extensibility
     */
    val payload: Array<KClass<out Any>> = []
  )

  /**
   * Validate that transactionIsolation is present if setTransactionIsolation is true
   */
  class Validator : ConstraintValidator<SpinValidated, SqlProperties> {
    override fun isValid(
      value: SqlProperties,
      context: ConstraintValidatorContext
    ): Boolean {
      if (value.setTransactionIsolation && (value.transactionIsolation == null)) {
        context.buildConstraintViolationWithTemplate("must specify transactionIsolation if setTransactionIsolation is true")
          .addConstraintViolation()
        return false
      }
      return true
    }
  }
}

internal class MisconfiguredConnectionPoolsException(message: String) : IllegalStateException(message) {
  companion object {
    val NEITHER_PRESENT = MisconfiguredConnectionPoolsException(
      "Neither 'sql.connectionPools' nor 'sql.connectionPool' have been configured"
    )
    val BOTH_PRESENT = MisconfiguredConnectionPoolsException(
      "Both 'sql.connectionPools' and 'sql.connectionPool' are configured: Use only one"
    )
  }
}
