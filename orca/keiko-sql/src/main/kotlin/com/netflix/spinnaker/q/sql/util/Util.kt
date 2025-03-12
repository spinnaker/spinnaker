/*
 * Copyright 2020 Apple, Inc.
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

package com.netflix.spinnaker.q.sql.util

import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.impl.DSL

fun currentSchema(context: DSLContext): String {
  return context.fetch("select current_schema()")
    .getValue(0, DSL.field("current_schema")).toString()
}

fun createTableLike(newTable: String, templateTable: String, context: DSLContext) {
  var sql = "CREATE TABLE IF NOT EXISTS "
  sql += when (context.dialect()) {
    SQLDialect.POSTGRES -> {
      val cs = currentSchema(context)
      "$cs.$newTable ( LIKE $cs.$templateTable INCLUDING ALL )"
    }
    else -> "$newTable LIKE $templateTable"
  }
  context.execute(sql)
}

// Allows insertion of virtual Postgres values on conflict, similar to MySQLDSL.values
fun <T> excluded(values: Field<T>): Field<T> {
  return DSL.field("excluded.{0}", values.dataType, values)
}
