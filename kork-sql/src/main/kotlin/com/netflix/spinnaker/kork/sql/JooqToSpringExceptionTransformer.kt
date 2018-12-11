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
package com.netflix.spinnaker.kork.sql

import org.jooq.ExecuteContext
import org.jooq.impl.DefaultExecuteListener
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator

class JooqToSpringExceptionTransformer : DefaultExecuteListener() {

  override fun exception(ctx: ExecuteContext) {
    if (ctx.sqlException() != null) {
      val dialect = ctx.configuration().dialect()
      val translator = if (dialect != null)
        SQLErrorCodeSQLExceptionTranslator(dialect.name)
      else
        SQLStateSQLExceptionTranslator()
      ctx.exception(translator.translate("jOOQ", ctx.sql(), ctx.sqlException()))
    }
  }
}
