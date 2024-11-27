/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.ProjectService
import groovy.util.logging.Slf4j
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RequestMapping("/projects")
@RestController
@Slf4j
class ProjectController {
  @Autowired
  ProjectService projectService

  @Operation(summary = "Get all projects")
  @RequestMapping(method = RequestMethod.GET)
  List<Map> all() {
    return projectService.getAll()
  }

  @Operation(summary = "Get a project")
  @RequestMapping(value = "/{id:.+}", method = RequestMethod.GET)
  Map get(@PathVariable("id") String projectId) {
    return projectService.get(projectId)
  }

  @Operation(summary = "Get a project's clusters")
  @RequestMapping(value = "/{id}/clusters", method = RequestMethod.GET)
  List<Map> getClusters(@PathVariable("id") String projectId,
                        @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    return projectService.getClusters(projectId, sourceApp)
  }

  @Operation(summary = "Get all pipelines for project")
  @RequestMapping(value = "/{id:.+}/pipelines", method = RequestMethod.GET)
  List<Map> allPipelinesForProject(@PathVariable("id") String projectId,
                                   @RequestParam(value = "limit", defaultValue = "5") int limit,
                                   @RequestParam(value = "statuses", required = false) String statuses) {
    return projectService.getAllPipelines(projectId, limit, statuses)
  }
}
