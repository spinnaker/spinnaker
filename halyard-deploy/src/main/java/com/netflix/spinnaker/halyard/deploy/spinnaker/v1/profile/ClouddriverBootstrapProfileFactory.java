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

import com.netflix.spinnaker.halyard.config.config.v1.ArtifactSources;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import com.netflix.spinnaker.halyard.config.model.v1.providers.consul.ConsulConfig;
import com.netflix.spinnaker.halyard.config.model.v1.providers.consul.SupportsConsul;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ClouddriverBootstrapProfileFactory extends SpringProfileFactory {
  @Autowired
  AccountService accountService;

  @Autowired
  ArtifactSources artifactSources;

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.CLOUDDRIVER;
  }

  @Override
  protected void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    super.setProfile(profile, deploymentConfiguration, endpoints);

    DeploymentEnvironment deploymentEnvironment = deploymentConfiguration.getDeploymentEnvironment();
    if (deploymentEnvironment.getType() != DeploymentEnvironment.DeploymentType.Distributed) {
      throw new IllegalStateException("There is no need to produce a bootstrapping clouddriver for a non-remote deployment of Spinnaker. This is a bug.");
    }

    Account account = accountService.getAnyProviderAccount(deploymentConfiguration.getName(), deploymentEnvironment.getAccountName());

    // We make a clone to modify a provider account only within the bootstrapping instance
    Providers providers = deploymentConfiguration.getProviders();
    Providers clonedProviders = providers.cloneNode(Providers.class);
    deploymentConfiguration.setProviders(clonedProviders);

    account.makeBootstrappingAccount(artifactSources);

    if (account instanceof SupportsConsul) {
      SupportsConsul consulAccount = (SupportsConsul) account;
      ConsulConfig config = consulAccount.getConsul();
      if (config == null) {
        config = new ConsulConfig();
        consulAccount.setConsul(config);
      }

      consulAccount.getConsul().setEnabled(true);
    } else {
      log.warn("Attempting to perform a distributed deployment to account \"" + account.getName() + "\" without a discovery mechanism");
    }

    List<String> files = backupRequiredFiles(providers, deploymentConfiguration.getName());
    profile.appendContents(yamlToString(providers))
        .appendContents(profile.getBaseContents())
        .setRequiredFiles(files);
  }
}
