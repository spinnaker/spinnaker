/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.cats.sql

import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.sql.ResultSet

object SqlUtil {

  fun createTableLike(jooq: DSLContext, baseName: String, template: String) {
    when (jooq.dialect()) {
      SQLDialect.POSTGRES ->
        jooq.execute("CREATE TABLE IF NOT EXISTS $baseName (LIKE $template INCLUDING ALL)")
      else ->
        jooq.execute(
          "CREATE TABLE IF NOT EXISTS $baseName LIKE $template"
        )
    }
  }

  fun getTablesLike(jooq: DSLContext, baseName: String): ResultSet {
    return when (jooq.dialect()) {
      SQLDialect.POSTGRES ->
        jooq.select(DSL.field("tablename"))
          .from(DSL.table("pg_catalog.pg_tables"))
          .where(DSL.field("tablename").like("$baseName%"))
          .fetch()
          .intoResultSet()
      else ->
        jooq.fetch("show tables like '$baseName%'").intoResultSet()
    }
  }

  fun <T> excluded(values: Field<T>): Field<T> {
    return DSL.field("excluded.{0}", values.dataType, values)
  }
}
