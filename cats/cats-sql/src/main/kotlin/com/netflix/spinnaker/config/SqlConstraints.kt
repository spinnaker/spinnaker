/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.config

import org.jooq.SQLDialect
import org.springframework.boot.context.properties.ConfigurationProperties

class SqlConstraints(
  val maxTableNameLength: Int,
  val maxIdLength: Int,
  val maxAgentLength: Int,
) {

  constructor(defaultConstraints: SqlConstraints, constraintsProperties: SqlConstraintsProperties) : this(
      constraintsProperties.maxTableNameLength ?: defaultConstraints.maxTableNameLength,
      constraintsProperties.maxIdLength ?: defaultConstraints.maxIdLength,
      constraintsProperties.maxAgentLength ?: defaultConstraints.maxAgentLength
  )
}

@ConfigurationProperties("sql.constraints")
class SqlConstraintsProperties {
  var maxTableNameLength: Int? = null
  var maxIdLength: Int? = null
  var maxAgentLength: Int? = null
}

object SqlConstraintsInitializer {

  fun getDefaultSqlConstraints(dialect: SQLDialect): SqlConstraints =
    when(dialect) {
      SQLDialect.POSTGRES ->
        // https://www.postgresql.org/docs/current/limits.html
        SqlConstraints(63, Int.MAX_VALUE, Int.MAX_VALUE)
      else ->
        // 352 * 2 + 64 (max rel_type length) == 768; 768 * 4 (utf8mb4) == 3072 == Aurora's max index length
        SqlConstraints(64, 352, 127)
    }
}
