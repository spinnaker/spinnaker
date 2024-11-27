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
import com.netflix.spinnaker.gate.services.PipelineTemplateService.PipelineTemplateDependent;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import io.swagger.v3.oas.annotations.Operation;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
@RequestMapping(value = "/pipelineTemplates")
public class PipelineTemplatesController {

  // TODO rz - make configurable?
  private static final String DEFAULT_APPLICATION = "spinnaker";

  private PipelineTemplateService pipelineTemplateService;
  private TaskService taskService;
  private ObjectMapper objectMapper;

  @Autowired
  public PipelineTemplatesController(
      PipelineTemplateService pipelineTemplateService,
      TaskService taskService,
      ObjectMapper objectMapper) {
    this.pipelineTemplateService = pipelineTemplateService;
    this.taskService = taskService;
    this.objectMapper = objectMapper;
  }

  @Operation(summary = "List pipeline templates.")
  @RequestMapping(method = RequestMethod.GET)
  public Collection<Map> list(@RequestParam(required = false) List<String> scopes) {
    return pipelineTemplateService.findByScope(scopes);
  }

  @Operation(summary = "Create a pipeline template.")
  @RequestMapping(method = RequestMethod.POST)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  public Map create(@RequestBody Map<String, Object> pipelineTemplate) {
    PipelineTemplate template;
    try {
      template = objectMapper.convertValue(pipelineTemplate, PipelineTemplate.class);
    } catch (IllegalArgumentException e) {
      throw new RuntimeException("Pipeline template is invalid", e);
    }

    List<Map<String, Object>> jobs = new ArrayList<>();
    Map<String, Object> job = new HashMap<>();
    job.put("type", "createPipelineTemplate");
    job.put("pipelineTemplate", encodeAsBase64(pipelineTemplate, objectMapper));
    job.put("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
    jobs.add(job);

    Map<String, Object> operation = new HashMap<>();
    operation.put(
        "description", "Create pipeline template '" + getNameFromTemplate(template) + "'");
    operation.put("application", getApplicationFromTemplate(template));
    operation.put("job", jobs);

    return taskService.create(operation);
  }

  @Operation(summary = "Resolve a pipeline template.")
  @RequestMapping(value = "/resolve", method = RequestMethod.GET)
  public Map resolveTemplates(
      @RequestParam String source,
      @RequestParam(required = false) String executionId,
      @RequestParam(required = false) String pipelineConfigId) {
    return pipelineTemplateService.resolve(source, executionId, pipelineConfigId);
  }

  @Operation(summary = "Get a pipeline template.")
  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  public Map get(@PathVariable String id) {
    return pipelineTemplateService.get(id);
  }

  @Operation(summary = "Update a pipeline template.")
  @RequestMapping(value = "/{id}", method = RequestMethod.POST)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  public Map update(
      @PathVariable String id,
      @RequestBody Map<String, Object> pipelineTemplate,
      @RequestParam(value = "skipPlanDependents", defaultValue = "false")
          boolean skipPlanDependents) {
    PipelineTemplate template;
    try {
      template = objectMapper.convertValue(pipelineTemplate, PipelineTemplate.class);
    } catch (IllegalArgumentException e) {
      throw new RuntimeException("Pipeline template is invalid", e);
    }

    List<Map<String, Object>> jobs = new ArrayList<>();
    Map<String, Object> job = new HashMap<>();
    job.put("type", "updatePipelineTemplate");
    job.put("id", id);
    job.put("pipelineTemplate", encodeAsBase64(pipelineTemplate, objectMapper));
    job.put("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
    job.put("skipPlanDependents", skipPlanDependents);
    jobs.add(job);

    Map<String, Object> operation = new HashMap<>();
    operation.put(
        "description", "Update pipeline template '" + getNameFromTemplate(template) + "'");
    operation.put("application", getApplicationFromTemplate(template));
    operation.put("job", jobs);

    return taskService.create(operation);
  }

  @Operation(summary = "Delete a pipeline template.")
  @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  public Map delete(
      @PathVariable String id,
      @RequestParam(value = "application", required = false) String application) {
    List<Map<String, Object>> jobs = new ArrayList<>();
    Map<String, Object> job = new HashMap<>();
    job.put("type", "deletePipelineTemplate");
    job.put("pipelineTemplateId", id);
    job.put("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
    jobs.add(job);

    Map<String, Object> operation = new HashMap<>();
    operation.put("description", "Delete pipeline template '" + id + "'");
    operation.put("application", application != null ? application : DEFAULT_APPLICATION);
    operation.put("job", jobs);

    return taskService.create(operation);
  }

  @Operation(summary = "List all pipelines that implement a pipeline template")
  @RequestMapping(value = "/{id}/dependents", method = RequestMethod.GET)
  public List<PipelineTemplateDependent> listPipelineTemplateDependents(
      @PathVariable String id,
      @RequestParam(value = "recursive", required = false) boolean recursive) {
    return pipelineTemplateService.getTemplateDependents(id, recursive);
  }

  static String getNameFromTemplate(PipelineTemplate template) {
    return Optional.ofNullable(template.metadata.name).orElse(template.id);
  }

  static String getApplicationFromTemplate(PipelineTemplate template) {
    List<String> scopes = template.metadata.scopes;
    return (scopes.isEmpty() || scopes.size() > 1) ? DEFAULT_APPLICATION : scopes.get(0);
  }

  static String encodeAsBase64(Object value, ObjectMapper objectMapper) {
    try {
      return Base64.getEncoder().encodeToString(objectMapper.writeValueAsString(value).getBytes());
    } catch (Exception e) {
      throw new RuntimeException("Could not encode pipeline template", e);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class PipelineTemplate {
    @JsonProperty String id;

    @JsonProperty Metadata metadata = new Metadata();

    static class Metadata {
      @JsonProperty String name;

      @JsonProperty List<String> scopes = new ArrayList<>();
    }
  }
}
