/*
 * Copyright 2016 Netflix, Inc.
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

import com.netflix.spinnaker.gate.services.EntityTagsService;
import com.netflix.spinnaker.gate.services.TaskService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(value = "/tags")
public class EntityTagsController {
  private EntityTagsService entityTagsService;
  private TaskService taskService;

  @Autowired
  public EntityTagsController(EntityTagsService entityTagsService, TaskService taskService) {
    this.entityTagsService = entityTagsService;
    this.taskService = taskService;
  }

  @RequestMapping(method = RequestMethod.GET)
  public Collection<Map> list(@RequestParam Map<String, Object> allParameters) {
    return entityTagsService.list(allParameters);
  }

  @RequestMapping(value = "/**", method = RequestMethod.GET)
  public Map get(HttpServletRequest request) {
    String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String id = new AntPathMatcher().extractPathWithinPattern(pattern, request.getServletPath());

    return entityTagsService.get(id);
  }

  @ApiOperation(value = "Deletes a subset of tags for the provided tag ID",
                notes = "multiple tags can be deleted for an entity using a comma as a separator, e.g. /tag1,tag2")
  @RequestMapping(value = "/{id}/{tag}", method = RequestMethod.DELETE)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  public Map delete(@PathVariable String id, @PathVariable String tag) {
    Map<String, Object> operation = new HashMap<>();
    List<Map<String, Object>> jobs = new ArrayList<>();
    Map<String, Object> job = new HashMap<>();
    job.put("type", "deleteEntityTags");
    job.put("id", id);
    job.put("tags", tag.split(","));
    jobs.add(job);
    operation.put("job", jobs);
    return taskService.create(operation);
  }

  @RequestMapping(method = RequestMethod.POST)
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  public Map post(@RequestParam("entityId") String entityId,
                  @RequestParam("entityType") String entityType,
                  @RequestParam(value = "account", defaultValue = "*") String account,
                  @RequestParam(value = "region", defaultValue = "*") String region,
                  @RequestParam(value = "cloudProvider", defaultValue = "*") String cloudProvider,
                  @RequestParam(value = "isPartial", defaultValue = "true") Boolean isPartial,
                  @RequestBody Map<String, Object> tags) {

    Map<String, String> entityRef = new HashMap<>();
    entityRef.put("entityId", entityId);
    entityRef.put("entityType", entityType);
    entityRef.put("account", account);
    entityRef.put("region", region);
    entityRef.put("cloudProvider", cloudProvider);

    Map<String, Object> job = new HashMap<>();
    job.put("type", "upsertEntityTags");
    job.put("entityRef", entityRef);
    job.put("isPartial", isPartial);
    job.put("tags", tags);

    List<Map<String, Object>> jobs = new ArrayList<>();
    jobs.add(job);

    Map<String, Object> operation = new HashMap<>();
    operation.put("job", jobs);
    return taskService.create(operation);
  }

}
