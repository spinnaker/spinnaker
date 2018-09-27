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

import com.netflix.spinnaker.security.AuthenticatedRequest
import org.jooq.ExecuteContext
import org.jooq.impl.DefaultExecuteListener
import org.slf4j.MDC

/**
 * Appends contextual information about the source of a SQL query for
 * debugging purposes.
 */
class JooqSqlCommentAppender : DefaultExecuteListener() {

  override fun renderEnd(ctx: ExecuteContext) {
    val comments: ArrayList<String> = ArrayList()

    if (AuthenticatedRequest.getSpinnakerExecutionId().isPresent) {
      comments.add("executionId: " + AuthenticatedRequest.getSpinnakerExecutionId().get())
    }

    if (AuthenticatedRequest.getSpinnakerUser().isPresent) {
      comments.add("user: " + AuthenticatedRequest.getSpinnakerUser().get())
    }

    if (AuthenticatedRequest.getSpinnakerUserOrigin().isPresent) {
      comments.add("origin: " + AuthenticatedRequest.getSpinnakerUserOrigin().get())
    }

    val poller: String? = MDC.get("agentClass")
    if (poller != null) {
      comments.add("agentClass: $poller")
    }

    if (comments.isNotEmpty()) {
      ctx.sql(ctx.sql() + comments.joinToString(prefix = " -- ", separator = " "))
    }
  }
}
