/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Repositories;
import com.netflix.spinnaker.halyard.config.model.v1.node.Search;
import com.netflix.spinnaker.halyard.config.services.v1.SearchService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericDeleteRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(
    "/v1/config/deployments/{deploymentName:.+}/repository/{repositoryName:.+}/searches")
public class SearchController {
  private final SearchService searchService;
  private final HalconfigParser halconfigParser;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<Search>> searches(
      @PathVariable String deploymentName,
      @PathVariable String repositoryName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<Search>>builder()
        .getter(() -> searchService.getAllSearches(deploymentName, repositoryName))
        .validator(() -> searchService.validateAllSearches(deploymentName, repositoryName))
        .description("Get all searches for " + repositoryName)
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{searchName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Search> search(
      @PathVariable String deploymentName,
      @PathVariable String repositoryName,
      @PathVariable String searchName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Search>builder()
        .getter(() -> searchService.getRepositorySearch(deploymentName, repositoryName, searchName))
        .validator(() -> searchService.validateSearch(deploymentName, repositoryName, searchName))
        .description("Get the " + searchName + " search")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{searchName:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deleteSearch(
      @PathVariable String deploymentName,
      @PathVariable String repositoryName,
      @PathVariable String searchName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericDeleteRequest.builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .deleter(() -> searchService.deleteSearch(deploymentName, repositoryName, searchName))
        .validator(() -> searchService.validateAllSearches(deploymentName, repositoryName))
        .description("Delete the " + searchName + " search")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{searchName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setSearch(
      @PathVariable String deploymentName,
      @PathVariable String repositoryName,
      @PathVariable String searchName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawSearch) {
    Search search =
        objectMapper.convertValue(rawSearch, Repositories.translateSearchType(repositoryName));
    return GenericUpdateRequest.<Search>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(s -> searchService.setSearch(deploymentName, repositoryName, searchName, s))
        .validator(
            () -> searchService.validateSearch(deploymentName, repositoryName, search.getName()))
        .description("Edit the " + searchName + " search")
        .build()
        .execute(validationSettings, search);
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addSearch(
      @PathVariable String deploymentName,
      @PathVariable String repositoryName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawSearch) {
    Search search =
        objectMapper.convertValue(rawSearch, Repositories.translateSearchType(repositoryName));
    return GenericUpdateRequest.<Search>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(s -> searchService.addSearch(deploymentName, repositoryName, s))
        .validator(
            () -> searchService.validateSearch(deploymentName, repositoryName, search.getName()))
        .description("Add the " + search.getName() + " search")
        .build()
        .execute(validationSettings, search);
  }
}
