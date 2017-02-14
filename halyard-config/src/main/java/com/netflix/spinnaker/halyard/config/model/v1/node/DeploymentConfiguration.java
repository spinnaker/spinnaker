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

import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
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
  String name = "default";

  /**
   * Version of Spinnaker being deployed (not to be confused with the halyard version).
   *
   * @see Halconfig#halyardVersion
   */
  String version = "latest";

  /**
   * Providers, e.g. Kubernetes, GCE, AWS, etc...
   */
  Providers providers = new Providers();

  /**
   * Details about how Spinnaker is deployed, e.g. which account is it running in, what's the footprint, etc...
   */
  DeploymentEnvironment deploymentEnvironment = new DeploymentEnvironment();

  /**
   * GCS/S3 configuration for front50.
   */
  PersistentStorage persistentStorage = new PersistentStorage();

  /**
   * Spinnaker feature flags.
   */
  Features features = new Features();

  String timezone = "America/Los_Angeles";

  /**
   * Webhooks, e.g. Jenkins, TravisCI, etc...
   */
  Webhooks webhooks = new Webhooks();

  /**
   * Authn & Authz configuration.
   */
  Security security = new Security();

  @Override
  public String getNodeName() {
    return name;
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeReflectiveIterator(this);
  }

  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }
}
