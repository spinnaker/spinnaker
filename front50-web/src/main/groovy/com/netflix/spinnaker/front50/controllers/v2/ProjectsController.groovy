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
import com.netflix.spinnaker.front50.model.project.Project
import com.netflix.spinnaker.front50.model.project.ProjectDAO
import com.netflix.spinnaker.front50.resources.project.ProjectResource
import com.netflix.spinnaker.front50.resources.project.ProjectResourceAssembler
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.hateoas.Resources
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.*

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

  private final ProjectResourceAssembler assembler = new ProjectResourceAssembler()

  @RequestMapping(value = "/search", method = RequestMethod.GET)
  @ApiOperation(value = "", notes = """Search for projects given one or more attributes.

- /search?q=ProjectName
- /search?q=ApplicationName
""")
  Resources<ProjectResource> search(@RequestParam("q") String query) {
    def projects = projectDAO.all().findAll { Project project ->
      project.name.toLowerCase().contains(query.toLowerCase()) || project.config.applications.find {
        it.toLowerCase().contains(query.toLowerCase())
      }
    }

    def resources = assembler.toResources(projects)
    def link = linkTo(ProjectsController).slash("/search?q=${query}").withSelfRel()
    return new Resources<ProjectResource>(resources, link)
  }

  @ApiOperation(value = "", notes = "Fetch all projects")
  @RequestMapping(method = RequestMethod.GET)
  Resources<ProjectResource> projects() {
    def resources = assembler.toResources(projectDAO.all())
    def link = linkTo(ProjectsController).slash('/').withSelfRel()
    return new Resources<ProjectResource>(resources, link)
  }

  @ApiOperation(value = "", notes = "Fetch a single project")
  @RequestMapping(method = RequestMethod.GET, value = "/{projectId}")
  ProjectResource project(@PathVariable String projectId) {
    try {
      return assembler.toResource(projectDAO.findBy("name", projectId))
    } catch (NotFoundException e) {
      return assembler.toResource(projectDAO.findBy("id", projectId))
    }
  }

  @ApiOperation(value = "", notes = "Update an existing project")
  @RequestMapping(method = RequestMethod.PUT, value = "/{projectId}")
  ProjectResource put(@PathVariable final String projectId, @RequestBody final Project project) {
    def existingProject = projectDAO.findBy("id", projectId)

    project.id = existingProject.id
    project.createTs = existingProject.createTs
    project.updateTs = System.currentTimeMillis()

    try {
      if (projectDAO.findBy("name", project.name).id != projectId) {
        // renamed projects must still be uniquely named
        throw new ProjectAlreadyExistsException()
      }
    } catch (NotFoundException ignored) {}

    return assembler.toResource(projectDAO.update(projectId, project))
  }

  @ApiOperation(value = "", notes = "Create a project")
  @RequestMapping(method = RequestMethod.POST)
  ProjectResource create(@RequestBody final Project project) {
    project.createTs = System.currentTimeMillis()
    project.updateTs = System.currentTimeMillis()

    try {
      projectDAO.findBy("name", project.name)
      throw new ProjectAlreadyExistsException()
    } catch (NotFoundException ignored) {}

    return assembler.toResource(projectDAO.create(project))
  }

  @ApiOperation(value = "", notes = "Delete a project")
  @RequestMapping(method = RequestMethod.DELETE, value = "/{projectId}")
  void delete(@PathVariable String projectId, HttpServletResponse response) {
    projectDAO.delete(projectId)
    response.setStatus(HttpStatus.ACCEPTED.value())
  }

  @RequestMapping(method = RequestMethod.DELETE)
  void truncate(HttpServletResponse response) {
    projectDAO.truncate()
    response.setStatus(HttpStatus.ACCEPTED.value())
  }

  @InheritConstructors
  @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Project already exists")
  class ProjectAlreadyExistsException extends RuntimeException {}
}
