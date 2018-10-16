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

package com.netflix.spinnaker.gate.controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.services.PipelineTemplateService.PipelineTemplateDependent;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.gate.services.V2PipelineTemplateService;
import io.swagger.annotations.ApiOperation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/v2/pipelineTemplates")
public class V2PipelineTemplatesController {

  private V2PipelineTemplateService pipelineTemplateService;
  private TaskService taskService;
  private ObjectMapper objectMapper;

  @Autowired
  public V2PipelineTemplatesController(V2PipelineTemplateService pipelineTemplateService,
                                       TaskService taskService,
                                       ObjectMapper objectMapper) {
    this.pipelineTemplateService = pipelineTemplateService;
    this.taskService = taskService;
    this.objectMapper = objectMapper;
  }

  // TODO(jacobkiefer): Un-stub
  @ApiOperation(value = "List pipeline templates.", response = HashMap.class, responseContainer = "List")
  @RequestMapping(method = RequestMethod.GET)
  public Collection<Map> list(@RequestParam(required = false) List<String> scopes) {
    return null;
  }

  @ApiOperation(value = "Create a pipeline template.", response = HashMap.class)
  @RequestMapping(method = RequestMethod.POST)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  public Map create(@RequestBody Map<String, Object> pipelineTemplate) {
    return null;
  }

  @ApiOperation(value = "Resolve a pipeline template.", response = HashMap.class)
  @RequestMapping(value = "/resolve", method = RequestMethod.GET)
  public Map resolveTemplates(@RequestParam String source, @RequestParam(required = false) String executionId, @RequestParam(required = false) String pipelineConfigId) {
    return null;
  }

  @ApiOperation(value = "Get a pipeline template.", response = HashMap.class)
  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  public Map get(@PathVariable String id) {
    return null;
  }

  @ApiOperation(value = "Update a pipeline template.", response = HashMap.class)
  @RequestMapping(value = "/{id}", method = RequestMethod.POST)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  public Map update(@PathVariable String id,
                    @RequestBody Map<String, Object> pipelineTemplate,
                    @RequestParam(value = "skipPlanDependents", defaultValue = "false") boolean skipPlanDependents) {
    return null;
  }

  @ApiOperation(value = "Delete a pipeline template.", response = HashMap.class)
  @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  public Map delete(@PathVariable String id,
                    @RequestParam(value = "application", required = false) String application) {
    return null;
  }

  @ApiOperation(value = "List all pipelines that implement a pipeline template", response = List.class)
  @RequestMapping(value = "/{id}/dependents", method = RequestMethod.GET)
  public List<PipelineTemplateDependent> listPipelineTemplateDependents(
    @PathVariable String id,
    @RequestParam(value = "recursive", required = false) boolean recursive
  ) {
    return null;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class PipelineTemplate {
    @JsonProperty
    String id;

    @JsonProperty
    Metadata metadata = new Metadata();

    static class Metadata {
      @JsonProperty
      String name;

      @JsonProperty
      List<String> scopes = new ArrayList<>();
    }
  }
}
