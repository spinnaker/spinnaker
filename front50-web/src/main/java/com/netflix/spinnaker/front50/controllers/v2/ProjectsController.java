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
package com.netflix.spinnaker.front50.controllers.v2;

import static java.lang.String.format;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.UntypedUtils;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.exceptions.InvalidRequestException;
import com.netflix.spinnaker.front50.model.SearchUtils;
import com.netflix.spinnaker.front50.model.project.Project;
import com.netflix.spinnaker.front50.model.project.ProjectDAO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;
import lombok.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/v2/projects", produces = MediaType.APPLICATION_JSON_VALUE)
@Api(value = "projects", description = "Project API")
public class ProjectsController {

  private static final Splitter COMMA_SPLITTER = Splitter.on(',');

  private MessageSource messageSource;
  private ProjectDAO projectDAO;
  private Registry registry;

  @RequestMapping(value = "/search", method = RequestMethod.GET)
  @ApiOperation(
      value = "",
      notes =
          "Search for projects given one or more attributes.\n\n- /search?q=ProjectName\n- /search?q=ApplicationName\n")
  public Set<Project> search(@RequestParam("q") final String query) {
    return projectDAO.all().stream()
        .filter(
            p ->
                p.getName().toLowerCase().contains(query.toLowerCase())
                    || p.getConfig().getApplications().stream()
                        .anyMatch(it -> it.toLowerCase().contains(query.toLowerCase())))
        .collect(Collectors.toSet());
  }

  @ApiOperation(
      value = "",
      notes =
          "Fetch all projects.\n\n    Support filtering by one or more attributes:\n    - ?name=projectName\n    - ?email=my@email.com")
  @RequestMapping(method = RequestMethod.GET)
  public List<Project> projects(
      @RequestParam(value = "pageSize", required = false) Integer pageSize,
      @RequestParam Map<String, String> params) {
    params.remove("pageSize");
    List<Project> projects =
        params.isEmpty() ? new ArrayList<>(projectDAO.all()) : filter(projectDAO.all(), params);
    return (pageSize == null) ? projects : projects.subList(0, Math.min(pageSize, projects.size()));
  }

  @ApiOperation(value = "", notes = "Fetch a single project")
  @RequestMapping(method = RequestMethod.GET, value = "/{projectId}")
  public Project project(@PathVariable String projectId) {
    try {
      return projectDAO.findByName(projectId);
    } catch (NotFoundException e) {
      return projectDAO.findById(projectId);
    }
  }

  @ApiOperation(value = "", notes = "Update an existing project")
  @RequestMapping(method = RequestMethod.PUT, value = "/{projectId}")
  public Project put(@PathVariable final String projectId, @RequestBody final Project project) {
    Project existingProject = projectDAO.findById(projectId);

    project.setId(existingProject.getId());
    project.setCreateTs(existingProject.getCreateTs());
    project.setUpdateTs(System.currentTimeMillis());

    try {
      if (!projectDAO.findByName(project.getName()).getId().equals(projectId)) {
        // renamed projects must still be uniquely named
        throw new InvalidRequestException(
            format("A Project named '%s' already exists", project.getName()));
      }
    } catch (NotFoundException ignored) {
    }

    projectDAO.update(projectId, project);
    return project;
  }

  @ApiOperation(value = "", notes = "Create a project")
  @RequestMapping(method = RequestMethod.POST)
  public Project create(@RequestBody final Project project) {
    project.setCreateTs(System.currentTimeMillis());
    project.setUpdateTs(System.currentTimeMillis());

    try {
      projectDAO.findByName(project.getName());
      throw new InvalidRequestException(
          format("A Project named '%s' already exists", project.getName()));
    } catch (NotFoundException ignored) {
    }

    return projectDAO.create(project.getId(), project);
  }

  private List<Project> filter(Collection<Project> projects, Map<String, String> attributes) {
    Map<String, String> normalizedAttributes = new HashMap<>();
    for (Map.Entry<String, String> attr : attributes.entrySet()) {
      if (!Strings.isNullOrEmpty(attr.getValue())) {
        normalizedAttributes.put(attr.getKey().toLowerCase(), attr.getValue());
      }
    }

    List<Project> items = new ArrayList<>();

    if (normalizedAttributes.containsKey("applications")) {
      List<String> applications =
          COMMA_SPLITTER.splitToList(normalizedAttributes.get("applications"));
      // TODO(rz): LOL converting Groovy with elvis operators everywhere. :grimacing:
      //  Do you know what's going on here? I certainly don't. Good thing there's tests?
      items =
          projects.stream()
              .filter(
                  project ->
                      Optional.ofNullable(project.getConfig().getApplications())
                              .map(
                                  projectApps ->
                                      projectApps.stream()
                                          .anyMatch(
                                              app -> projectApplicationMatches(app, applications)))
                              .orElse(false)
                          || Optional.ofNullable(project.getConfig().getClusters())
                              .map(
                                  projectClusters ->
                                      projectClusters.stream()
                                          .anyMatch(
                                              cluster ->
                                                  clusterHasMatchingApplication(
                                                      cluster, applications)))
                              .orElse(false))
              .collect(Collectors.toList());
      normalizedAttributes.remove("applications");
    }

    items.addAll(
        projects.stream()
            .filter(
                project ->
                    normalizedAttributes.entrySet().stream()
                        .anyMatch(
                            it ->
                                FUZZY_SEARCH_PREDICATE.test(
                                    new FuzzySearch(project, it.getKey(), it.getValue()))))
            .collect(Collectors.toList()));

    return items.stream()
        .distinct()
        .sorted(
            (a, b) ->
                SearchUtils.score(b, normalizedAttributes)
                    - SearchUtils.score(a, normalizedAttributes))
        .collect(Collectors.toList());
  }

  private static boolean projectApplicationMatches(String app, List<String> applications) {
    return applications.stream().anyMatch(it -> app.toLowerCase().contains(it.toLowerCase()));
  }

  private static boolean clusterHasMatchingApplication(
      Project.ClusterConfig cluster, List<String> applications) {
    return Optional.ofNullable(cluster.getApplications())
        .map(
            clusterApplications ->
                clusterApplications.stream()
                    .anyMatch(
                        app ->
                            applications.stream()
                                .anyMatch(it -> app.toLowerCase().contains(it.toLowerCase()))))
        .orElse(false);
  }

  @ApiOperation(value = "", notes = "Delete a project")
  @RequestMapping(method = RequestMethod.DELETE, value = "/{projectId}")
  public void delete(@PathVariable String projectId, HttpServletResponse response) {
    projectDAO.delete(projectId);
    response.setStatus(HttpStatus.ACCEPTED.value());
  }

  @RequestMapping(method = RequestMethod.POST, value = "/batchUpdate")
  public void batchUpdate(
      @RequestBody final Collection<Project> projects, HttpServletResponse response) {
    projectDAO.bulkImport(projects);
    response.setStatus(HttpStatus.ACCEPTED.value());
  }

  private static final Predicate<FuzzySearch> FUZZY_SEARCH_PREDICATE =
      fuzzySearch -> {
        boolean matches =
            SearchUtils.matchesIgnoreCase(
                UntypedUtils.getProperties(fuzzySearch.project),
                fuzzySearch.key,
                fuzzySearch.value);
        if (!matches) {
          return fuzzySearch.project.getConfig().getClusters().stream()
              .anyMatch(
                  it ->
                      SearchUtils.matchesIgnoreCase(
                          UntypedUtils.getProperties(it), fuzzySearch.key, fuzzySearch.value));
        }
        return true;
      };

  @Value
  private static class FuzzySearch {
    Project project;
    String key;
    String value;
  }
}
