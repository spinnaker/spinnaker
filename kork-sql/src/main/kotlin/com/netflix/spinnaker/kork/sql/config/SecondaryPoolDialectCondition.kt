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

package com.netflix.spinnaker.kork.sql.config

import org.springframework.boot.autoconfigure.condition.ConditionOutcome
import org.springframework.boot.autoconfigure.condition.SpringBootCondition
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

class SecondaryPoolDialectCondition : SpringBootCondition() {

  override fun getMatchOutcome(context: ConditionContext?, metadata: AnnotatedTypeMetadata?): ConditionOutcome {
    return ConditionOutcome(hasDifferentDialect(context), "SQL Dialect check did not pass")
  }

  fun hasDifferentDialect(context: ConditionContext?): Boolean {
    val sqlProperties: SqlProperties = Binder.get(context?.environment)
      .bind(ConfigurationPropertyName.of("sql"), Bindable.of(SqlProperties::class.java))
      .orElse(SqlProperties())

    if (sqlProperties.connectionPools.size <= 1 || sqlProperties.connectionPools.size > 2) {
      return false
    }

    val defaultPool: ConnectionPoolProperties = sqlProperties.connectionPools.first(default = true)
    val secondaryPool: ConnectionPoolProperties = sqlProperties.connectionPools.first(default = false)

    return defaultPool.dialect != secondaryPool.dialect
  }

  private fun MutableMap<String, ConnectionPoolProperties>.first(default: Boolean): ConnectionPoolProperties =
    filter {
      if (default) {
        it.value.default
      } else {
        !it.value.default
      }
    }.values.first()
}
