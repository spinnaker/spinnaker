/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.echo.pipelinetriggers.postprocessors;

import com.netflix.spinnaker.echo.model.Pipeline;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This class is a thin wrapper around the implementations of {@link PipelinePostProcessor}. It
 * loops through the ordered list of post-processors and applies the transformation defined by each
 * to the input pipeline.
 */
@Component
public class PipelinePostProcessorHandler {
  private List<PipelinePostProcessor> postProcessors;

  @Autowired
  public PipelinePostProcessorHandler(List<PipelinePostProcessor> postProcessors) {
    postProcessors.sort(Comparator.comparing(PipelinePostProcessor::priority));
    this.postProcessors = postProcessors;
  }

  public Pipeline process(Pipeline inputPipeline) {
    Pipeline pipeline = inputPipeline;
    for (PipelinePostProcessor postProcessor : postProcessors) {
      pipeline = postProcessor.processPipeline(pipeline);
    }
    return pipeline;
  }
}
