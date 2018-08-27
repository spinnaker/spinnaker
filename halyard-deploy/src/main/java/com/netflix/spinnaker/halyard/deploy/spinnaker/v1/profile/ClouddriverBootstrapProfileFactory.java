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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.config.v1.ArtifactSourcesConfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.config.model.v1.providers.containers.ContainerAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.consul.ConsulConfig;
import com.netflix.spinnaker.halyard.config.model.v1.providers.consul.SupportsConsul;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryProvider;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ClouddriverBootstrapProfileFactory extends SpringProfileFactory {

  private final String DOCKER_REGISTRY = Provider.ProviderType.DOCKERREGISTRY.getName();

  @Autowired
  AccountService accountService;

  @Autowired
  ArtifactSourcesConfig artifactSourcesConfig;

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.CLOUDDRIVER;
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    super.setProfile(profile, deploymentConfiguration, endpoints);

    DeploymentEnvironment deploymentEnvironment = deploymentConfiguration.getDeploymentEnvironment();
    if (deploymentEnvironment.getType() != DeploymentEnvironment.DeploymentType.Distributed) {
      throw new IllegalStateException("There is no need to produce a bootstrapping clouddriver for a non-remote deployment of Spinnaker. This is a bug.");
    }

    // We need to make modifications to this deployment configuration, but can't use helpful objects
    // like the accountService on a clone. Therefore, we'll make the modifications in place and
    // restore to the original state when the modifications are written out.
    Providers originalProviders = deploymentConfiguration.getProviders().cloneNode(Providers.class);
    Providers modifiedProviders = deploymentConfiguration.getProviders();

    String deploymentName = deploymentConfiguration.getName();
    String bootstrapAccountName = deploymentEnvironment.getAccountName();

    Account bootstrapAccount = accountService.getAnyProviderAccount(deploymentName, bootstrapAccountName);
    bootstrapAccount.makeBootstrappingAccount(artifactSourcesConfig);

    Provider bootstrapProvider = (Provider) bootstrapAccount.getParent();
    disableAllProviders(modifiedProviders);

    bootstrapProvider.setEnabled(true);
    bootstrapProvider.setAccounts(Collections.singletonList(bootstrapAccount));

    if (bootstrapAccount instanceof ContainerAccount) {
      ContainerAccount containerAccount = (ContainerAccount) bootstrapAccount;

      List<DockerRegistryAccount> bootstrapRegistries = containerAccount.getDockerRegistries()
          .stream()
          .map(ref -> (DockerRegistryAccount) accountService.getProviderAccount(deploymentName, DOCKER_REGISTRY, ref.getAccountName()))
          .collect(Collectors.toList());

      DockerRegistryProvider dockerProvider = modifiedProviders.getDockerRegistry();
      dockerProvider.setEnabled(true);
      dockerProvider.setAccounts(bootstrapRegistries);
    }

    if (bootstrapAccount instanceof SupportsConsul) {
      SupportsConsul consulAccount = (SupportsConsul) bootstrapAccount;
      ConsulConfig config = consulAccount.getConsul();
      if (config == null) {
        config = new ConsulConfig();
        consulAccount.setConsul(config);
      }

      consulAccount.getConsul().setEnabled(true);
    } else {
      log.warn("Attempting to perform a distributed deployment to account \"" + bootstrapAccount.getName() + "\" without a discovery mechanism");
    }

    List<String> files = backupRequiredFiles(modifiedProviders, deploymentConfiguration.getName());
    profile.appendContents(yamlToString(modifiedProviders))
        .appendContents("services.fiat.enabled: false")
        .appendContents(profile.getBaseContents())
        .setRequiredFiles(files);

    deploymentConfiguration.setProviders(originalProviders);
  }

  private void disableAllProviders(Providers providers) {
    NodeIterator providerNodes = providers.getChildren();
    Provider provider;
    while ((provider = (Provider) providerNodes.getNext()) != null) {
      provider.setEnabled(false);
    }
  }
}
