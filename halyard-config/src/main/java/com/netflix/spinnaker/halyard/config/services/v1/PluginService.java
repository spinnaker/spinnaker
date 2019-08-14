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
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Plugins;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PluginService {
  private final LookupService lookupService;
  private final ValidateService validateService;
  private final DeploymentService deploymentService;

  private Plugins getPlugins(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setPlugin();

    return lookupService.getSingularNodeOrDefault(
        filter, Plugins.class, Plugins::new, n -> setPlugins(deploymentName, n));
  }

  private void setPlugins(String deploymentName, Plugins newPlugins) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    deploymentConfiguration.setPlugins(newPlugins);
  }

  public List<Plugin> getAllPlugins(String deploymentName) {
    return getPlugins(deploymentName).getPlugins();
  }

  public Plugin getPlugin(String deploymentName, String pluginName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setPlugin(pluginName);
    List<Plugin> matchingPlugins = lookupService.getMatchingNodesOfType(filter, Plugin.class);

    switch (matchingPlugins.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Problem.Severity.FATAL, "No plugin with name \"" + pluginName + "\" was found")
                .setRemediation("Create a new plugin with name \"" + pluginName + "\"")
                .build());
      case 1:
        return matchingPlugins.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Problem.Severity.FATAL,
                    "More than one plugin named \"" + pluginName + "\" was found")
                .setRemediation(
                    "Manually delete/rename duplicate plugins with name \""
                        + pluginName
                        + "\" in your halconfig file")
                .build());
    }
  }

  public void setPlugin(String deploymentName, String pluginName, Plugin newPlugin) {
    List<Plugin> plugins = getAllPlugins(deploymentName);
    for (int i = 0; i < plugins.size(); i++) {
      if (plugins.get(i).getNodeName().equals(pluginName)) {
        plugins.set(i, newPlugin);
        return;
      }
    }
    throw new HalException(
        new ConfigProblemBuilder(
                Problem.Severity.FATAL, "Plugin \"" + pluginName + "\" wasn't found")
            .build());
  }

  public void deletePlugin(String deploymentName, String pluginName) {
    List<Plugin> plugins = getAllPlugins(deploymentName);
    boolean removed = plugins.removeIf(plugin -> plugin.getName().equals(pluginName));

    if (!removed) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Problem.Severity.FATAL, "Plugin \"" + pluginName + "\" wasn't found")
              .build());
    }
  }

  public void addPlugin(String deploymentName, Plugin newPlugin) {
    String newPluginName = newPlugin.getName();
    List<Plugin> plugins = getAllPlugins(deploymentName);
    for (Plugin plugin : plugins) {
      if (plugin.getName().equals(newPluginName)) {
        throw new HalException(
            new ConfigProblemBuilder(
                    Problem.Severity.FATAL, "Plugin \"" + newPluginName + "\" already exists")
                .build());
      }
    }
    plugins.add(newPlugin);
  }

  public void setPluginsEnabled(String deploymentName, boolean validate, boolean enable) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    Plugins plugins = deploymentConfiguration.getPlugins();
    plugins.setEnabled(enable);
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
