/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.memory

import com.netflix.spinnaker.keel.tracing.Trace
import com.netflix.spinnaker.keel.tracing.TraceRepository
import org.slf4j.LoggerFactory
import javax.annotation.PostConstruct

class MemoryTraceRepository : TraceRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  private val traces = mutableMapOf<String, MutableList<Trace>>()

  @PostConstruct fun init() {
    log.info("Using ${javaClass.simpleName}")
  }

  override fun record(trace: Trace) {
    if (!traces.containsKey(trace.intent.id())) {
      traces[trace.intent.id()] = mutableListOf()
    }
    traces[trace.intent.id()]?.add(trace)
  }

  override fun getForIntent(intentId: String) = traces[intentId]?.toList() ?: listOf()
}
