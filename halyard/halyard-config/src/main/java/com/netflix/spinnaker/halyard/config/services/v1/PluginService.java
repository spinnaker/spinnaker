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
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PluginService {
  private final LookupService lookupService;
  private final ValidateService validateService;
  private final DeploymentService deploymentService;

  public Map<String, Plugin> getPlugins(String deploymentName) {
    return deploymentService
        .getDeploymentConfiguration(deploymentName)
        .getSpinnaker()
        .getExtensibility()
        .getPlugins();
  }

  public Plugin getPlugin(String deploymentName, String pluginName) {
    Plugin plugin = getPlugins(deploymentName).get(pluginName);
    if (plugin == null) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(
                  Problem.Severity.FATAL, "No plugin with name \"" + pluginName + "\" was found")
              .setRemediation("Create a new plugin with name \"" + pluginName + "\"")
              .build());
    }
    return plugin;
  }

  public void deletePlugin(String deploymentName, String pluginName) {
    Map<String, Plugin> plugins = getPlugins(deploymentName);
    Plugin plugin = plugins.remove(pluginName);

    if (plugin == null) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Problem.Severity.FATAL, "Plugin \"" + pluginName + "\" wasn't found")
              .build());
    }
  }

  public void addPlugin(String deploymentName, Plugin newPlugin) {
    Map<String, Plugin> plugins = getPlugins(deploymentName);
    if (plugins.containsKey(newPlugin.getId())) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Problem.Severity.FATAL, "Plugin \"" + newPlugin.getId() + "\" already exists")
              .build());
    }
    plugins.put(newPlugin.getId(), newPlugin);
  }

  public ProblemSet validateAllPlugins(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyPlugin();
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validatePlugin(String deploymentName, String pluginName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setPlugin(pluginName);
    return validateService.validateMatchingFilter(filter);
  }
}
