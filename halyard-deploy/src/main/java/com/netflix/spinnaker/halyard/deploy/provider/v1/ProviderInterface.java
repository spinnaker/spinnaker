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

package com.netflix.spinnaker.halyard.deploy.provider.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.endpoint.EndpointType;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.endpoint.ServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A ProviderInterface is an abstraction for communicating with a specific cloud-provider's installation
 * of Spinnaker.
 */
@Component
public abstract class ProviderInterface<T extends Account> {
  @Autowired
  JobExecutor jobExecutor;

  @Autowired
  ServiceFactory serviceFactory;

  @Autowired
  String spinnakerOutputPath;

  @Autowired
  DeploymentService deploymentService;

  @Autowired
  ArtifactService artifactService;

  /**
   * @param details are the deployment details for the current deployment.
   * @param artifact is the artifact who's version to fetch.
   * @return the docker image/debian package/etc... for a certain profile.
   */
  abstract protected String componentArtifact(DeploymentDetails<T> details, SpinnakerArtifact artifact);

  abstract public Object connectTo(DeploymentDetails<T> details, EndpointType endpointType);

  abstract public void bootstrapClouddriver(DeploymentDetails<T> details);
}
