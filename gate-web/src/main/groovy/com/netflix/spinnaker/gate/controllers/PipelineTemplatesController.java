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
package com.netflix.spinnaker.gate.controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.services.PipelineTemplateService;
import com.netflix.spinnaker.gate.services.TaskService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/pipelineTemplates")
public class PipelineTemplatesController {

  // TODO rz - make configurable?
  private static final String DEFAULT_APPLICATION = "spinnaker";

  private PipelineTemplateService pipelineTemplateService;
  private TaskService taskService;
  private ObjectMapper objectMapper;

  @Autowired
  public PipelineTemplatesController(PipelineTemplateService pipelineTemplateService, TaskService taskService, ObjectMapper objectMapper) {
    this.pipelineTemplateService = pipelineTemplateService;
    this.taskService = taskService;
    this.objectMapper = objectMapper;
  }

  @ApiOperation(value = "Returns a list of pipeline templates by scope",
                notes = "If no scope is provided, 'global' will be defaulted")
  @RequestMapping(method = RequestMethod.GET)
  public Collection<Map> list(@RequestParam(defaultValue = "global") List<String> scopes) {
    return pipelineTemplateService.findByScope(scopes);
  }

  @RequestMapping(method = RequestMethod.POST)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  public Map create(@RequestBody Map<String, Object> pipelineTemplate) {
    List<Map<String, Object>> jobs = new ArrayList<>();
    Map<String, Object> job = new HashMap<>();
    job.put("type", "createPipelineTemplate");
    job.put("pipelineTemplate", pipelineTemplate);
    jobs.add(job);

    Map<String, Object> operation = new HashMap<>();
    operation.put("description", "Create pipeline template");
    operation.put("application", getApplicationFromTemplate(pipelineTemplate));
    operation.put("job", jobs);

    return taskService.create(operation);
  }

  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  public Map get(@PathVariable String id) {
    return pipelineTemplateService.get(id);
  }

  @RequestMapping(value = "/{id}", method = RequestMethod.POST)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  public Map update(@PathVariable String id, @RequestBody Map<String, Object> pipelineTemplate) {
    List<Map<String, Object>> jobs = new ArrayList<>();
    Map<String, Object> job = new HashMap<>();
    job.put("type", "updatePipelineTemplate");
    job.put("id", id);
    job.put("pipelineTemplate", pipelineTemplate);
    jobs.add(job);

    Map<String, Object> operation = new HashMap<>();
    operation.put("description", "Update pipeline template '" + id + "'");
    operation.put("application", getApplicationFromTemplate(pipelineTemplate));
    operation.put("job", jobs);

    return taskService.create(operation);
  }

  private String getApplicationFromTemplate(Map<String, Object> pipelineTemplate) {
    PipelineTemplate template;
    try {
      template = objectMapper.convertValue(pipelineTemplate, PipelineTemplate.class);
    } catch (IllegalArgumentException e) {
      return DEFAULT_APPLICATION;
    }
    List<String> scopes = template.metadata.scopes;
    return (scopes.isEmpty() || scopes.size() > 1) ? DEFAULT_APPLICATION : scopes.get(0);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class PipelineTemplate {
    @JsonProperty
    private Metadata metadata = new Metadata();

    private static class Metadata {
      @JsonProperty
      private List<String> scopes = new ArrayList<>();
    }
  }
}
