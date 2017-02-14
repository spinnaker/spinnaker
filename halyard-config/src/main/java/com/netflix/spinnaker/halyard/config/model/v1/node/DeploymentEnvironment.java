/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Arrays;

/**
 * A DeploymentEnvironment is a location where Spinnaker is installed.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DeploymentEnvironment extends Node {
  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }

  @Override
  public String getNodeName() {
    return "deploymentEnvironment";
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeEmptyIterator();
  }

  public enum DeploymentType {
    Flotilla("Deploy Spinnaker with one server group and load balancer "
        + "per microservice, and a single instance of Redis acting as "
        + "Spinnaker's cache layer. This requires a cloud provider to deploy to."),
    LocalhostDebian("Deploy Spinnaker locally (on the machine running the daemon) "
        + "using `apt-get` to fetch all the service's debian packages.");

    @Getter
    final String description;

    DeploymentType(String description) {
      this.description = description;
    }

    public static DeploymentType fromString(String name) {
      for (DeploymentType type : DeploymentType.values()) {
        if (type.toString().equalsIgnoreCase(name)) {
          return type;
        }
      }

      throw new IllegalArgumentException("DeploymentType \"" + name + "\" is not a valid choice. The options are: "
          + Arrays.toString(DeploymentType.values()));
    }
  }


  private DeploymentType type = DeploymentType.LocalhostDebian;
  private String accountName;
}
