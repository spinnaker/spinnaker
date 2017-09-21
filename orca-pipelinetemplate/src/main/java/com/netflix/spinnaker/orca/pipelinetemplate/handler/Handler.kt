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
package com.netflix.spinnaker.orca.pipelinetemplate.handler

interface Handler {
  fun handle(chain: HandlerChain, context: PipelineTemplateContext)
}

interface HandlerGroup {
  fun getHandlers(): List<Handler>
}

interface HandlerChain {
  fun add(handler: Handler)
  fun add(handlerChain: HandlerGroup)
  fun removeFirst(): Handler
  fun isEmpty(): Boolean
  fun clear()
}

class DefaultHandlerChain : HandlerChain {

  private val chain = mutableListOf<Handler>()

  override fun add(handler: Handler) {
    chain.add(handler)
  }

  override fun add(handlerChain: HandlerGroup) {
    chain.addAll(handlerChain.getHandlers())
  }

  override fun removeFirst() = chain.removeAt(0)

  override fun isEmpty() = chain.isEmpty()

  override fun clear() {
    chain.clear()
  }
}
