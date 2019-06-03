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
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the
 * current halconfigs repositories.
 */
@Component
public class RepositoryService {
  @Autowired private LookupService lookupService;

  @Autowired private ValidateService validateService;

  public Repository getRepository(String deploymentName, String repositoryName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setRepository(repositoryName);

    List<Repository> matching = lookupService.getMatchingNodesOfType(filter, Repository.class);

    switch (matching.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL,
                    "No repository service with name \"" + repositoryName + "\" could be found")
                .build());
      case 1:
        return matching.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL,
                    "More than one repository with name \"" + repositoryName + "\" found")
                .build());
    }
  }

  public List<Repository> getAllRepositories(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyRepository();

    List<Repository> matching = lookupService.getMatchingNodesOfType(filter, Repository.class);

    if (matching.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No repositories could be found").build());
    } else {
      return matching;
    }
  }

  public void setEnabled(String deploymentName, String repositoryName, boolean enabled) {
    Repository repository = getRepository(deploymentName, repositoryName);
    repository.setEnabled(enabled);
  }

  public ProblemSet validateRepository(String deploymentName, String repositoryName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setRepository(repositoryName)
            .withAnyAccount();

    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllRepositories(String deploymentName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).withAnyRepository().withAnyAccount();

    return validateService.validateMatchingFilter(filter);
  }
}
