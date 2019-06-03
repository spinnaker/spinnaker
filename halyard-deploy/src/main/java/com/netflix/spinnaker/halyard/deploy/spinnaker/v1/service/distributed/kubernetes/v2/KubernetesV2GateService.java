/*
 * Copyright 2018 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2;

import com.netflix.spinnaker.halyard.config.model.v1.ha.HaServices;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.GateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedService.DeployPriority;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.KubernetesSharedServiceSettings;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Data
@Component
@EqualsAndHashCode(callSuper = true)
public class KubernetesV2GateService extends GateService
    implements KubernetesV2Service<GateService.Gate> {
  final DeployPriority deployPriority = new DeployPriority(0);

  @Delegate @Autowired KubernetesV2ServiceDelegate serviceDelegate;

  @Override
  public ServiceSettings defaultServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    return new Settings(
        deploymentConfiguration.getSecurity().getApiSecurity(),
        getActiveSpringProfiles(deploymentConfiguration));
  }

  @Override
  public ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    KubernetesSharedServiceSettings kubernetesSharedServiceSettings =
        new KubernetesSharedServiceSettings(deploymentConfiguration);
    ServiceSettings settings = defaultServiceSettings(deploymentConfiguration);
    settings
        .setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setLocation(kubernetesSharedServiceSettings.getDeployLocation())
        .setEnabled(true);
    return settings;
  }

  @Override
  protected boolean hasServiceOverrides(DeploymentConfiguration deployment) {
    HaServices haServices = deployment.getDeploymentEnvironment().getHaServices();
    return haServices.getClouddriver().isEnabled() || haServices.getEcho().isEnabled();
  }

  @Override
  protected List<Type> overrideServiceEndpoints() {
    return Arrays.asList(Type.CLOUDDRIVER_RO, Type.ECHO_WORKER);
  }

  @Override
  protected void appendReadonlyClouddriverForDeck(
      Profile profile,
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints) {
    if (hasServiceOverrides(deploymentConfiguration)
        && !deploymentConfiguration
            .getDeploymentEnvironment()
            .getHaServices()
            .getClouddriver()
            .isDisableClouddriverRoDeck()) {
      Map<String, Map<String, Map<String, Map<String, Map<String, String>>>>> services =
          Collections.singletonMap(
              "services",
              Collections.singletonMap(
                  "clouddriver",
                  Collections.singletonMap(
                      "config",
                      Collections.singletonMap(
                          "dynamicEndpoints",
                          Collections.singletonMap(
                              "deck",
                              endpoints
                                  .getServiceSettings(Type.CLOUDDRIVER_RO_DECK)
                                  .getBaseUrl())))));
      profile.appendContents(getYamlParser().dump(services));
    }
  }
}
