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
package com.netflix.spinnaker.echo.pipelinetriggers.postprocessors;

import com.google.common.base.Strings;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Automatically sets a pipeline's execution ID if one was supplied by the eventing trigger. */
@Component
public class ProvidedExecutionIdPipelinePostProcessor implements PipelinePostProcessor {

  private final Logger log =
      LoggerFactory.getLogger(ProvidedExecutionIdPipelinePostProcessor.class);

  @Override
  public Pipeline processPipeline(Pipeline inputPipeline) {
    Trigger trigger = inputPipeline.getTrigger();
    if (!Strings.isNullOrEmpty(trigger.getExecutionId())) {
      log.debug(
          "Assigning trigger-provided execution ID to pipeline: '{}'", trigger.getExecutionId());
      return inputPipeline.withExecutionId(trigger.getExecutionId());
    }
    return inputPipeline;
  }
}
