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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSetBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A DeploymentConfiguration is an installation of Spinnaker, described in your Halconfig.
 *
 * @see Halconfig
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DeploymentConfiguration extends Node {
  /**
   * Human-readable name for this deployment of Spinnaker.
   */
  String name;

  /**
   * Version of Spinnaker being deployed (not to be confused with the halyard version).
   *
   * @see Halconfig#halyardVersion
   */
  String version;

  /**
   * Providers, e.g. Kubernetes, GCE, AWS, ...
   */
  Providers providers;

  /**
   * Webhooks, e.g. Jenkins, TravisCI, ...
   */
  Webhooks webhooks;

  @Override
  public String getNodeName() {
    return name;
  }

  @Override
  boolean matchesLocally(NodeFilter filter) {
    return NodeFilter.matches(filter.deployment, name);
  }

  @Override
  public NodeReference getReference() {
    return parent.getReference().setDeployment(name);
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeReflectiveIterator(this);
  }

  @Override
  public void accept(ProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }
}
