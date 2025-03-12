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

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers

import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import spock.lang.Specification

class EventHandlerSupport extends Specification {
  public PipelineCache pipelineCache(Pipeline... pipelines) {
    return pipelineCache(Arrays.asList(pipelines))
  }

  public PipelineCache pipelineCache(List<Pipeline> pipelines) {
    def cache = Mock(PipelineCache)
    def decoratedPipelines = PipelineCache.decorateTriggers(pipelines)
    cache.getEnabledTriggersSync() >> PipelineCache.extractEnabledTriggersFrom(decoratedPipelines)
    return cache
  }
}
