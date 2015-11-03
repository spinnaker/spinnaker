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
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/projects")
@RestController
@Slf4j
class ProjectController {
  @Autowired
  ProjectService projectService

  @RequestMapping(method = RequestMethod.GET)
  List<Map> all() {
    return projectService.getAll()
  }

  @RequestMapping(value = "/{id:.+}", method = RequestMethod.GET)
  Map get(@PathVariable("id") String projectId) {
    def result = projectService.get(projectId)
    if (!result) {
      log.warn("Project not found (projectId: ${projectId}")
      throw new ProjectNotFoundException("Project not found (projectId: ${projectId})")
    }
    result
  }

  @RequestMapping(value = "/{id:.+}/pipelines", method = RequestMethod.GET)
  List<Map> allPipelinesForProject(@PathVariable("id") String projectId,
                                   @RequestParam(value = "limit", defaultValue = "5") int limit,
                                   @RequestParam(value = "statuses", required = false) String statuses) {
    return projectService.getAllPipelines(projectId, limit, statuses)
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @InheritConstructors
  static class ProjectNotFoundException extends RuntimeException {}
}
