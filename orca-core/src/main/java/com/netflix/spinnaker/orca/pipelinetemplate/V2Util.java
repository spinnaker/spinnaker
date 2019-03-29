/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate;

import com.netflix.spinnaker.orca.extensionpoint.pipeline.PipelinePreprocessor;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class V2Util {
  public static Map<String, Object> planPipeline(ContextParameterProcessor contextParameterProcessor,
                                                 List<PipelinePreprocessor> pipelinePreprocessors,
                                                 Map<String, Object> pipeline) {
    // TODO(jacobkiefer): Excise the logic in OperationsController that requires plan to avoid resolving artifacts.
    pipeline.put("plan", true); // avoid resolving artifacts
    for (PipelinePreprocessor pp : pipelinePreprocessors) {
      pipeline = pp.process(pipeline);
    }

    Map<String, Object> augmentedContext = new HashMap<>();
    augmentedContext.put("trigger", pipeline.get("trigger"));
    augmentedContext.put("templateVariables", pipeline.getOrDefault("templateVariables", Collections.EMPTY_MAP));
    return contextParameterProcessor.process(pipeline, augmentedContext, false);
  }
}
