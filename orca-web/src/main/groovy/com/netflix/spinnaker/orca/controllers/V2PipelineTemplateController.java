/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.controllers;

import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(value = "/v2/pipelineTemplates")
@ConditionalOnExpression("${pipelineTemplates.enabled:true}")
@Slf4j
public class V2PipelineTemplateController {

  @Autowired
  private OperationsController operationsController;

  @Autowired
  ContextParameterProcessor contextParameterProcessor;

  @RequestMapping(value = "/plan", method = RequestMethod.POST)
  Map<String, Object> orchestrate(@RequestBody Map<String, Object> pipeline) {
    pipeline = operationsController.parseAndValidatePipeline(pipeline);

    Map<String, Object> augmentedContext = new HashMap<>();
    augmentedContext.put("trigger", pipeline.get("trigger"));
    augmentedContext.put("templateVariables", pipeline.getOrDefault("templateVariables", Collections.EMPTY_MAP));
    return contextParameterProcessor.process(pipeline, augmentedContext, false);
  }
}
