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
package com.netflix.spinnaker.orca.sql

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.jooq.ExecuteContext
import org.jooq.impl.DefaultExecuteListener
import org.slf4j.LoggerFactory
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Simple query logger, for when you don't/can't run general query logging in prod (looking at you, Aurora).
 *
 * When enabled, healthcheck queries will not be reported by default.
 */
class QueryLogger(
  private val dynamicConfigService: DynamicConfigService
) : DefaultExecuteListener() {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun end(ctx: ExecuteContext) {
    if (dynamicConfigService.isEnabled("sql.query-logger", false)) {
      val statements = ctx.batchSQL().joinToString("\n")
      val exclude = dynamicConfigService.getConfig(
        String::class.java,
        "sql.query-logger.exclude-pattern",
        ".*healthcheck.*"
      )
      if (exclude.isNotEmpty()) {
        val pattern: Pattern? = try {
          Pattern.compile(exclude)
        } catch (e: PatternSyntaxException) {
          log.error("Exclusion pattern invalid: '$exclude'", e)
          null
        }
        if (pattern?.matcher(statements)?.matches() == true) {
          return
        }
      }
      log.debug(statements)
    }
  }
}
