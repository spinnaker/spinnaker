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


package com.netflix.spinnaker.front50.controllers.v2

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.exceptions.InvalidRequestException
import com.netflix.spinnaker.front50.model.SearchUtils
import com.netflix.spinnaker.front50.model.project.Project
import com.netflix.spinnaker.front50.model.project.ProjectDAO
import groovy.util.logging.Slf4j
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import javax.servlet.http.HttpServletResponse

@Slf4j
@RestController
@RequestMapping(value = "/v2/projects", produces = MediaType.APPLICATION_JSON_VALUE)
@Api(value = "projects", description = "Project API")
public class ProjectsController {
  @Autowired
  MessageSource messageSource

  @Autowired
  ProjectDAO projectDAO

  @Autowired
  Registry registry

  @RequestMapping(value = "/search", method = RequestMethod.GET)
  @ApiOperation(value = "", notes = """Search for projects given one or more attributes.

- /search?q=ProjectName
- /search?q=ApplicationName
""")
  Set<Project> search(@RequestParam("q") String query) {
    def projects = projectDAO.all().findAll { Project project ->
      project.name.toLowerCase().contains(query.toLowerCase()) || project.config.applications.find {
        it.toLowerCase().contains(query.toLowerCase())
      }
    }

    return projects
  }

  @ApiOperation(value = "", notes = """Fetch all projects.

    Support filtering by one or more attributes:
    - ?name=projectName
    - ?email=my@email.com""")
  @RequestMapping(method = RequestMethod.GET)
  Set<Project> projects(@RequestParam(value = "pageSize", required = false) Integer pageSize, @RequestParam Map<String, String> params) {
    params.remove("pageSize")
    Set<Project> projects = params.isEmpty() ? projectDAO.all() : filter(projectDAO.all(), params)
    return pageSize ? projects.asList().subList(0, Math.min(pageSize, projects.size())) : projects
  }

  @ApiOperation(value = "", notes = "Fetch a single project")
  @RequestMapping(method = RequestMethod.GET, value = "/{projectId}")
  Project project(@PathVariable String projectId) {
    try {
      return projectDAO.findByName(projectId)
    } catch (NotFoundException e) {
      return projectDAO.findById(projectId)
    }
  }

  @ApiOperation(value = "", notes = "Update an existing project")
  @RequestMapping(method = RequestMethod.PUT, value = "/{projectId}")
  Project put(@PathVariable final String projectId, @RequestBody final Project project) {
    def existingProject = projectDAO.findById(projectId)

    project.id = existingProject.id
    project.createTs = existingProject.createTs
    project.updateTs = System.currentTimeMillis()

    try {
      if (projectDAO.findByName(project.name).id != projectId) {
        // renamed projects must still be uniquely named
        throw new InvalidRequestException("A Project named ${project.name} already exists")
      }
    } catch (NotFoundException ignored) {}

    projectDAO.update(projectId, project)
    return project
  }

  @ApiOperation(value = "", notes = "Create a project")
  @RequestMapping(method = RequestMethod.POST)
  Project create(@RequestBody final Project project) {
    project.createTs = System.currentTimeMillis()
    project.updateTs = System.currentTimeMillis()

    try {
      projectDAO.findByName(project.name)
      throw new InvalidRequestException("A Project named ${project.name} already exists")
    } catch (NotFoundException ignored) {}

    return projectDAO.create(project.id, project)
  }

  Set<Project> filter(Collection<Project> projects, Map<String, String> attributes) {
    attributes = attributes.collect {
      k,v -> [ k.toLowerCase(), v ]
    }.findAll { k, v -> v }.collectEntries()

    Set<Project> items = new HashSet<>()

    if (attributes.containsKey("applications")) {
      List<String> applications = attributes.applications.split(",")
      items = projects.findAll { project ->
        project.config.applications?.find { app -> applications.any { app.toLowerCase().contains(it.toLowerCase()) } } ||
          project.config.clusters?.find { cluster ->
            cluster.applications?.find { app -> applications.any { app.toLowerCase().contains(it.toLowerCase()) } }
          }
      }
      attributes.remove("applications")
    }

    items += projects.findAll { project ->
      boolean test = false
      attributes.each { k, v ->
        if (fuzzyPredicate(project, k, v)) {
          test = true
        }
      }
      return test
    }

    items.sort { Project a, Project b ->
      return SearchUtils.score(b, attributes) - SearchUtils.score(a, attributes)
    }

  }

  def fuzzyPredicate = { Project project, String key, String value  ->
    boolean matches = SearchUtils.matchesIgnoreCase(project.properties, key, value)
    if (!matches) {
      if (project.config.clusters.find { SearchUtils.matchesIgnoreCase(it.properties, key, value) }) {
        return true
      }
    }

    return matches
  }

  @ApiOperation(value = "", notes = "Delete a project")
  @RequestMapping(method = RequestMethod.DELETE, value = "/{projectId}")
  void delete(@PathVariable String projectId, HttpServletResponse response) {
    projectDAO.delete(projectId)
    response.setStatus(HttpStatus.ACCEPTED.value())
  }

  @RequestMapping(method = RequestMethod.POST, value = "/batchUpdate")
  void batchUpdate(@RequestBody final Collection<Project> projects, HttpServletResponse response) {
    projectDAO.bulkImport(projects)
    response.setStatus(HttpStatus.ACCEPTED.value())
  }
}
