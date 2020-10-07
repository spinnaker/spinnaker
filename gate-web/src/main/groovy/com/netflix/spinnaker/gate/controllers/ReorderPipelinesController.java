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

package com.netflix.spinnaker.gate.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.services.PipelineService;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import groovy.transform.CompileStatic;
import groovy.util.logging.Slf4j;
import io.swagger.annotations.ApiOperation;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@CompileStatic
@RestController
@RequestMapping("actions")
public class ReorderPipelinesController {
  @Autowired ObjectMapper objectMapper;

  @Autowired PipelineService pipelineService;

  @Autowired TaskService taskService;

  @ApiOperation(value = "Re-order pipelines")
  @RequestMapping(value = "/pipelines/reorder", method = RequestMethod.POST)
  public Map reorderPipelines(@RequestBody ReorderPipelinesCommand reorderPipelinesCommand) {
    return handlePipelineReorder(reorderPipelinesCommand, false);
  }

  @ApiOperation(value = "Re-order pipeline strategies")
  @RequestMapping(value = "/strategies/reorder", method = RequestMethod.POST)
  public Map reorderPipelineStrategies(
      @RequestBody ReorderPipelinesCommand reorderPipelinesCommand) {
    return handlePipelineReorder(reorderPipelinesCommand, true);
  }

  private Map handlePipelineReorder(
      ReorderPipelinesCommand reorderPipelinesCommand, Boolean isStrategy) {
    Map<String, Integer> idsToIndices = reorderPipelinesCommand.getIdsToIndices();
    var application = reorderPipelinesCommand.getApplication();

    if (idsToIndices == null) {
      throw new InvalidRequestException("`idsToIndices` is required field on request body");
    }

    if (application == null) {
      throw new InvalidRequestException("`application` is required field on request body");
    }

    List<Map<String, Object>> jobs = new ArrayList<>();
    Map<String, Object> job = new HashMap<>();
    job.put("type", "reorderPipelines");
    job.put("idsToIndices", encodeAsBase64(idsToIndices, objectMapper));
    job.put("isStrategy", encodeAsBase64(isStrategy, objectMapper));
    job.put("application", encodeAsBase64(application, objectMapper));
    job.put("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
    jobs.add(job);

    Map<String, Object> operation = new HashMap<>();
    operation.put("description", "Reorder pipelines");
    operation.put("application", application);
    operation.put("job", jobs);

    return taskService.createAndWaitForCompletion(operation);
  }

  @Data
  private static class ReorderPipelinesCommand {
    private Map<String, Integer> idsToIndices;
    private String application;
  }

  static String encodeAsBase64(Object value, ObjectMapper objectMapper) {
    try {
      return Base64.getEncoder().encodeToString(objectMapper.writeValueAsString(value).getBytes());
    } catch (Exception e) {
      throw new RuntimeException("Could not encode value", e);
    }
  }
}
