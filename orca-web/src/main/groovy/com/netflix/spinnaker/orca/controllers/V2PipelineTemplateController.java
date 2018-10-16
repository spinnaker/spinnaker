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

import com.netflix.spinnaker.orca.pipelinetemplate.PipelineTemplateService;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import groovy.util.logging.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@ConditionalOnExpression("${pipelineTemplates.enabled:true}")
@RestController
@Slf4j
public class V2PipelineTemplateController {

  @Autowired
  private PipelineTemplateService pipelineTemplateService;

  // TODO(jacobkiefer): Add fiat authz
  // TODO(jacobkiefer): Un-stub
  @RequestMapping(value = "/pipelineTemplate", method = RequestMethod.GET)
  PipelineTemplate getPipelineTemplate(@RequestParam Map template,
                                       @RequestParam(required = false) String executionId,
                                       @RequestParam(required = false) String pipelineConfigId) {
    return null;
  }

  @RequestMapping(value = "/convertPipelineToTemplate", method = RequestMethod.POST)
  String convertPipelineToPipelineTemplate(@RequestBody Map<String, Object> pipeline) {
    return null;
  }
}
