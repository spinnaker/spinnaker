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
import org.springframework.stereotype.Component;

/**
 * Post-processor extracts artifacts from a pipeline using a supplied Jinja template and adds
 * these artifacts to the pipeline as received artifacts.
 * This post-processor is not implemented yet and is currently a no-op.
 */
@Component
public class ArtifactPostProcessor implements PipelinePostProcessor {
  public Pipeline processPipeline(Pipeline inputPipeline) {
    // TODO(ezimanyi): implement this
    return inputPipeline;
  }

  public PostProcessorPriority priority() {
    return PostProcessorPriority.ARTIFACT_EXTRACTION;
  }
}
