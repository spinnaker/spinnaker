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
import com.netflix.spinnaker.halyard.config.spinnaker.v1.component.ComponentConfig;
import com.netflix.spinnaker.halyard.config.spinnaker.v1.component.SpinnakerComponent;
import com.netflix.spinnaker.halyard.deploy.component.v1.ComponentType;
import com.netflix.spinnaker.halyard.deploy.component.v1.ServiceFactory;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.job.v1.JobExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  @Autowired(required = false)
  List<SpinnakerComponent> spinnakerComponents = new ArrayList<>();

  Map<String, SpinnakerComponent> componentMap = null;

  protected SpinnakerComponent getComponentByName(String name) {
    if (componentMap == null) {
      componentMap = new HashMap<>();
      spinnakerComponents.forEach(c -> componentMap.put(c.getComponentName(), c));
    }

    return componentMap.get(name);
  }

  /**
   * @return the docker image/debian package/etc... for a certain component.
   */
  abstract protected String componentArtifact(DeploymentDetails<T> details, SpinnakerComponent component);

  abstract public Object connectTo(DeploymentDetails<T> details, ComponentType componentType);

  abstract public void bootstrapClouddriver(DeploymentDetails<T> details);
}
