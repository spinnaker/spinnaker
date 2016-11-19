/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIteratorFactory;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the entire contents of ~/.hal/config.
 */
@Data
public class Halconfig implements Node {
  /**
   * Version of Halyard required to manage this deployment.
   */
  private String halyardVersion;

  /**
   * Current deployment being managed.
   *
   * @see DeploymentConfiguration#getName()
   */
  private String currentDeployment;

  /**
   * List of available deployments.
   */
  private List<DeploymentConfiguration> deploymentConfigurations = new ArrayList<>();

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();

    result.append("version: ")
        .append(halyardVersion)
        .append('\n');

    result.append("currentDeployment: ")
        .append(currentDeployment)
        .append('\n');

    result.append("deployments: ");

    if (deploymentConfigurations.isEmpty()) {
      result.append("null");
    }

    result.append('\n');

    for (DeploymentConfiguration deployment : deploymentConfigurations) {
      result.append("  - ")
          .append(deployment.getName())
          .append('\n');
    }

    return result.toString();
  }

  @Override
  public void accept(Validator v) {
    v.validate(this);
  }

  @Override
  public String getNodeName() {
    return "halconfig";
  }

  @Override
  public NodeIterator getIterator() {
    return NodeIteratorFactory.getReflectiveIterator(this);
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.ROOT;
  }
}
