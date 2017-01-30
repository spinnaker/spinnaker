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

import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSetBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * A DeploymentEnvironment is a location where Spinnaker is installed.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DeploymentEnvironment extends Node {
  @Override
  public void accept(ProblemSetBuilder psBuilder, Validator v) {
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

  @Override
  protected boolean matchesLocally(NodeFilter filter) {
    return true;
  }

  @Override
  public NodeFilter getFilter() {
    return parent.getFilter();
  }

  public enum DeploymentType {
    ClusteredSimple("Deploy Spinnaker with one server group and load balancer "
        + "per microservice. This requires a cloud provider to deploy to."),
    LocalhostDebian("Deploy Spinnaker locally (on the machine running the daemon) "
        + "using `apt-get` to fetch all the service's debian packages.");

    @Getter
    final String description;

    DeploymentType(String description) {
      this.description = description;
    }
  }

  private DeploymentType type = DeploymentType.LocalhostDebian;
  private String accountName;
}
