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

import static com.netflix.spinnaker.gate.controllers.PipelineTemplatesController.encodeAsBase64;
import static com.netflix.spinnaker.gate.controllers.PipelineTemplatesController.getApplicationFromTemplate;
import static com.netflix.spinnaker.gate.controllers.PipelineTemplatesController.getNameFromTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.controllers.PipelineTemplatesController.PipelineTemplate;
import com.netflix.spinnaker.gate.services.PipelineTemplateService.PipelineTemplateDependent;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.gate.services.V2PipelineTemplateService;
import com.netflix.spinnaker.kork.web.exceptions.HasAdditionalAttributes;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import groovy.transform.InheritConstructors;
import io.swagger.annotations.ApiOperation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
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

  private static final String DEFAULT_APPLICATION = "spinnaker";
  private static final String SCHEMA = "schema";
  private static final String V2_SCHEMA_VERSION = "v2";

  private V2PipelineTemplateService v2PipelineTemplateService;
  private TaskService taskService;
  private ObjectMapper objectMapper;

  @Autowired
  public V2PipelineTemplatesController(V2PipelineTemplateService v2PipelineTemplateService,
                                       TaskService taskService,
                                       ObjectMapper objectMapper) {
    this.v2PipelineTemplateService = v2PipelineTemplateService;
    this.taskService = taskService;
    this.objectMapper = objectMapper;
  }

  @ApiOperation(value = "(ALPHA) List pipeline templates.", response = List.class)
  @RequestMapping(method = RequestMethod.GET)
  public Collection<Map> list(@RequestParam(required = false) List<String> scopes) {
    return v2PipelineTemplateService.findByScope(scopes);
  }

  @ApiOperation(value = "(ALPHA) Plan a pipeline template configuration.", response = HashMap.class)
  @RequestMapping(value = "/plan", method = RequestMethod.POST)
  public Map<String, Object> plan(@RequestBody Map<String, Object> pipeline) {
    return v2PipelineTemplateService.plan(pipeline);
  }

  @ApiOperation(value = "(ALPHA) Create a pipeline template.", response = HashMap.class)
  @RequestMapping(value = "/create", method = RequestMethod.POST)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  public Map create(@RequestParam(value = "tag", required = false) String tag, @RequestBody Map<String, Object> pipelineTemplate) {
    validateSchema(pipelineTemplate);
    Map<String, Object> operation = makeCreateOp(pipelineTemplate, tag);
    return taskService.createAndWaitForCompletion(operation);
  }

  private Map<String, Object> makeCreateOp(Map<String, Object> pipelineTemplate, String tag) {
    PipelineTemplate template;
    try {
      template = objectMapper.convertValue(pipelineTemplate, PipelineTemplate.class);
    } catch (IllegalArgumentException e) {
      throw new PipelineTemplateException("Pipeline template is invalid");
    }

    List<Map<String, Object>> jobs = new ArrayList<>();
    Map<String, Object> job = new HashMap<>();
    job.put("type", "createV2PipelineTemplate");
    job.put("pipelineTemplate", encodeAsBase64(pipelineTemplate, objectMapper));
    job.put("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
    if (!StringUtils.isEmpty(tag)) {
      job.put("tag", tag);
    }
    jobs.add(job);

    Map<String, Object> operation = new HashMap<>();
    operation.put("description", "Create pipeline template '" + getNameFromTemplate(template) + "'");
    operation.put("application", getApplicationFromTemplate(template));
    operation.put("job", jobs);
    return operation;
  }

  private void validateSchema(Map<String, Object> pipelineTemplate) {
    String schema = (String) pipelineTemplate.get(SCHEMA);
    if (schema != null && !schema.equals(V2_SCHEMA_VERSION)) {
      throw new RuntimeException("Pipeline template schema version is invalid");
    } else if (schema == null) {
      pipelineTemplate.put(SCHEMA, V2_SCHEMA_VERSION);
    }
  }

  @ApiOperation(value = "(ALPHA) Update a pipeline template.", response = HashMap.class)
  @RequestMapping(value = "/update/{id}", method = RequestMethod.POST)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  public Map update(@PathVariable String id,
    @RequestParam(value = "tag", required = false) String tag,
    @RequestBody Map<String, Object> pipelineTemplate,
    @RequestParam(value = "skipPlanDependents", defaultValue = "false") boolean skipPlanDependents) {
    Map<String, Object> operation = makeUpdateOp(pipelineTemplate, id, skipPlanDependents, tag);
    return taskService.createAndWaitForCompletion(operation);
  }

  private Map<String, Object> makeUpdateOp(Map<String, Object> pipelineTemplate, String id,
                                           boolean skipPlanDependents, String tag) {
    PipelineTemplate template;
    try {
      template = objectMapper.convertValue(pipelineTemplate, PipelineTemplate.class);
    } catch (IllegalArgumentException e) {
      throw new PipelineTemplateException("Pipeline template is invalid");
    }

    List<Map<String, Object>> jobs = new ArrayList<>();
    Map<String, Object> job = new HashMap<>();
    job.put("type", "updateV2PipelineTemplate");
    job.put("id", id);
    job.put("pipelineTemplate", encodeAsBase64(pipelineTemplate, objectMapper));
    job.put("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
    job.put("skipPlanDependents", skipPlanDependents);
    if (!StringUtils.isEmpty(tag)) {
      job.put("tag", tag);
    }
    jobs.add(job);

    Map<String, Object> operation = new HashMap<>();
    operation.put("description", "Update pipeline template '" + getNameFromTemplate(template) + "'");
    operation.put("application", getApplicationFromTemplate(template));
    operation.put("job", jobs);
    return operation;
  }

  @ApiOperation(value = "(ALPHA) Get a pipeline template.", response = HashMap.class)
  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  public Map get(@PathVariable String id,
    @RequestParam(value = "tag", required = false) String tag,
    @RequestParam(value = "digest", required = false) String digest) {
    return v2PipelineTemplateService.get(id, tag, digest);
  }

  @ApiOperation(value = "Delete a pipeline template.", response = HashMap.class)
  @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  public Map delete(@PathVariable String id,
                    @RequestParam(value = "tag", required = false) String tag,
                    @RequestParam(value = "digest", required = false) String digest,
                    @RequestParam(value = "application", required = false) String application) {
    List<Map<String, Object>> jobs = new ArrayList<>();
    Map<String, Object> job = new HashMap<>();
    job.put("type", "deleteV2PipelineTemplate");
    job.put("pipelineTemplateId", id);
    job.put("tag", tag);
    job.put("digest", digest);
    job.put("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
    jobs.add(job);

    Map<String, Object> operation = new HashMap<>();
    operation.put("description", "Delete pipeline template '" + id + "'");
    operation.put("application", application != null ? application : DEFAULT_APPLICATION);
    operation.put("job", jobs);

    return taskService.createAndWaitForCompletion(operation);
  }

  @ApiOperation(value = "(ALPHA) List all pipelines that implement a pipeline template", response = List.class)
  @RequestMapping(value = "/{id}/dependents", method = RequestMethod.GET)
  public List<PipelineTemplateDependent> listPipelineTemplateDependents(@PathVariable String id) {
    return v2PipelineTemplateService.getTemplateDependents(id);
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @InheritConstructors
  class PipelineTemplateException extends RuntimeException implements HasAdditionalAttributes {
    Map<String, Object> additionalAttributes = Collections.EMPTY_MAP;

    PipelineTemplateException(String message) {
      super(message);
    }

    PipelineTemplateException(Map<String, Object> additionalAttributes) {
      this.additionalAttributes = additionalAttributes;
    }
  }
}
