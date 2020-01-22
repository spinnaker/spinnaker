/*
 * Copyright 2019 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.PluginRepository;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PluginRepositoryService {
  private final LookupService lookupService;
  private final ValidateService validateService;
  private final DeploymentService deploymentService;

  public Map<String, PluginRepository> getPluginRepositories(String deploymentName) {
    return deploymentService
        .getDeploymentConfiguration(deploymentName)
        .getSpinnaker()
        .getExtensibility()
        .getRepositories();
  }

  public PluginRepository getPluginRepository(String deploymentName, String repositoryId) {
    PluginRepository pluginRepository = getPluginRepositories(deploymentName).get(repositoryId);
    if (pluginRepository == null) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(
                  Problem.Severity.FATAL,
                  "No plugin repository with id \"" + repositoryId + "\" was found")
              .setRemediation("Create a new plugin repository with id \"" + repositoryId + "\"")
              .build());
    }
    return pluginRepository;
  }

  public void setPluginRepository(
      String deploymentName, String pluginRepositoryId, PluginRepository newPluginRepository) {
    Map<String, PluginRepository> pluginRepositories = getPluginRepositories(deploymentName);
    if (pluginRepositories.get(pluginRepositoryId) == null) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Problem.Severity.FATAL,
                  "Plugin repository \"" + pluginRepositoryId + "\" wasn't found")
              .build());
    }
    pluginRepositories.put(pluginRepositoryId, newPluginRepository);
  }

  public void deletePluginRepository(String deploymentName, String repositoryId) {
    PluginRepository pluginRepository = getPluginRepositories(deploymentName).remove(repositoryId);
    if (pluginRepository == null) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Problem.Severity.FATAL, "Plugin repository \"" + repositoryId + "\" wasn't found")
              .build());
    }
  }

  public void addPluginRepository(String deploymentName, PluginRepository newPluginRepository) {
    Map<String, PluginRepository> pluginRepositories = getPluginRepositories(deploymentName);
    if (pluginRepositories.containsKey(newPluginRepository.getId())) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Problem.Severity.FATAL,
                  "Plugin repository \"" + newPluginRepository.getId() + "\" already exists")
              .build());
    }
    pluginRepositories.put(newPluginRepository.getId(), newPluginRepository);
  }

  public ProblemSet validateAllPluginRepositories(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyPluginRepository();
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validatePluginRepository(String deploymentName, String pluginRepositoryId) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setPluginRepository(pluginRepositoryId);
    return validateService.validateMatchingFilter(filter);
  }
}
