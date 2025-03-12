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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Repository;
import com.netflix.spinnaker.halyard.config.model.v1.node.Search;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the
 * current halconfig's searches.
 */
@Component
public class SearchService {
  @Autowired private LookupService lookupService;

  @Autowired private RepositoryService repositoryService;

  @Autowired private ValidateService validateService;

  public List<Search> getAllSearches(String deploymentName, String repositoryName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setRepository(repositoryName)
            .withAnySearch();

    List<Search> matchingSearches = lookupService.getMatchingNodesOfType(filter, Search.class);

    if (matchingSearches.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No searches could be found").build());
    } else {
      return matchingSearches;
    }
  }

  private Search getSearch(NodeFilter filter, String searchName) {
    List<Search> matchingSearches = lookupService.getMatchingNodesOfType(filter, Search.class);

    switch (matchingSearches.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL, "No search with name \"" + searchName + "\" was found")
                .setRemediation(
                    "Check if this search was defined in another repository service, or create a new one")
                .build());
      case 1:
        return matchingSearches.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL, "More than one search named \"" + searchName + "\" was found")
                .setRemediation(
                    "Manually delete/rename duplicate searches with name \""
                        + searchName
                        + "\" in your halconfig file")
                .build());
    }
  }

  public Search getRepositorySearch(
      String deploymentName, String repositoryName, String searchName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setRepository(repositoryName)
            .setSearch(searchName);
    return getSearch(filter, searchName);
  }

  public Search getAnyRepositorySearch(String deploymentName, String searchName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).withAnyRepository().setSearch(searchName);
    return getSearch(filter, searchName);
  }

  public void setSearch(
      String deploymentName, String repositoryName, String searchName, Search newSearch) {
    Repository repository = repositoryService.getRepository(deploymentName, repositoryName);

    for (int i = 0; i < repository.getSearches().size(); i++) {
      Search search = (Search) repository.getSearches().get(i);
      if (search.getNodeName().equals(searchName)) {
        repository.getSearches().set(i, newSearch);
        return;
      }
    }

    throw new HalException(
        new ConfigProblemBuilder(Severity.FATAL, "Search \"" + searchName + "\" wasn't found")
            .build());
  }

  public void deleteSearch(String deploymentName, String repositoryName, String searchName) {
    Repository repository = repositoryService.getRepository(deploymentName, repositoryName);
    boolean removed =
        repository.getSearches().removeIf(search -> ((Search) search).getName().equals(searchName));

    if (!removed) {
      throw new HalException(
          new ConfigProblemBuilder(Severity.FATAL, "Search \"" + searchName + "\" wasn't found")
              .build());
    }
  }

  public void addSearch(String deploymentName, String repositoryName, Search newSearch) {
    Repository repository = repositoryService.getRepository(deploymentName, repositoryName);
    repository.getSearches().add(newSearch);
  }

  public ProblemSet validateSearch(
      String deploymentName, String repositoryName, String searchName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setRepository(repositoryName)
            .setSearch(searchName);
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllSearches(String deploymentName, String repositoryName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setRepository(repositoryName)
            .withAnySearch();
    return validateService.validateMatchingFilter(filter);
  }
}
