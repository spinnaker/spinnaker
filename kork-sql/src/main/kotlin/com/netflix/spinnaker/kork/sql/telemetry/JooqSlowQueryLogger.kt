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
package com.netflix.spinnaker.kork.sql.telemetry

import org.jooq.ExecuteContext
import org.jooq.impl.DefaultExecuteListener
import org.jooq.tools.StopWatch
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class JooqSlowQueryLogger(
  slowQuerySecondsThreshold: Long = 1
) : DefaultExecuteListener() {

  private lateinit var watch: StopWatch

  private val log = LoggerFactory.getLogger(javaClass)
  private val slowQueryThreshold = TimeUnit.SECONDS.toNanos(slowQuerySecondsThreshold)

  override fun executeStart(ctx: ExecuteContext) {
    super.executeStart(ctx)
    watch = StopWatch()
  }

  override fun executeEnd(ctx: ExecuteContext) {
    super.executeEnd(ctx)
    if (watch.split() > slowQueryThreshold) {
      log.warn("Slow SQL (${watch.splitToMillis()}ms):\n${ctx.query()}")
    }
  }

  private fun StopWatch.splitToMillis() = TimeUnit.NANOSECONDS.toMillis(split())
}
