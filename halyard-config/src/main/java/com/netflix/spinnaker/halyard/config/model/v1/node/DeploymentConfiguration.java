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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.VersionsService;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.stream.Collectors;

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
  String version = "nightly";

  /**
   * Providers, e.g. Kubernetes, GCE, AWS, etc...
   */
  Providers providers = new Providers();

  /**
   * Details about how Spinnaker is deployed, e.g. which account is it running in, what's the footprint, etc...
   */
  DeploymentEnvironment deploymentEnvironment = new DeploymentEnvironment();

  /**
   * Persistent storage configuration for front50.
   */
  PersistentStorage persistentStorage = new PersistentStorage();

  /**
   * Spinnaker feature flags.
   */
  Features features = new Features();

  /**
   * Metric Store configuraton
   */
  MetricStores metricStores = new MetricStores();

  String timezone = "America/Los_Angeles";

  /**
   * Continuous integration services, e.g. Jenkins, TravisCI, etc...
   */
  Cis ci = new Cis();

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

  @JsonCreator
  public DeploymentConfiguration() {}

  protected List<String> versionOptions(ConfigProblemSetBuilder psBuilder) {
    VersionsService service = psBuilder.getContext().getBean(VersionsService.class);
    return service.getVersions().getVersions().stream().map(Version::getVersion).collect(Collectors.toList());
  }
}
