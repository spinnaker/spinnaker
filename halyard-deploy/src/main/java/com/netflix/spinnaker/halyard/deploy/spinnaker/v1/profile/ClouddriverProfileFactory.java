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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.config.model.v1.providers.containers.ContainerAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.containers.DockerRegistryReference;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryProvider;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ClouddriverProfileFactory extends SpringProfileFactory {

  @Autowired
  AccountService accountService;

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.CLOUDDRIVER;
  }

  @Override
  protected void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    super.setProfile(profile, deploymentConfiguration, endpoints);

    // We need to make modifications to this deployment configuration, but can't use helpful objects
    // like the accountService on a clone. Therefore, we'll make the modifications in place and
    // restore to the original state when the modifications are written out.
    Providers originalProviders = deploymentConfiguration.getProviders().cloneNode(Providers.class);
    Providers modifiedProviders = deploymentConfiguration.getProviders();

    DeploymentEnvironment deploymentEnvironment = deploymentConfiguration.getDeploymentEnvironment();
    if (deploymentEnvironment.getBootstrapOnly()) {
      String bootstrapAccountName = deploymentEnvironment.getAccountName();
      removeBootstrapOnlyAccount(modifiedProviders, deploymentConfiguration.getName(), bootstrapAccountName);
    }

    List<String> files = backupRequiredFiles(modifiedProviders, deploymentConfiguration.getName());
    profile.appendContents(yamlToString(modifiedProviders))
        .appendContents(profile.getBaseContents())
        .setRequiredFiles(files);

    deploymentConfiguration.setProviders(originalProviders);
  }

  @SuppressWarnings("unchecked")
  private void removeBootstrapOnlyAccount(Providers providers, String deploymentName, String bootstrapAccountName) {

    Account bootstrapAccount = accountService.getAnyProviderAccount(deploymentName, bootstrapAccountName);
    Provider bootstrapProvider = ((Provider) bootstrapAccount.getParent());

    bootstrapProvider.getAccounts().remove(bootstrapAccount);
    if (bootstrapProvider.getAccounts().isEmpty()) {
      bootstrapProvider.setEnabled(false);

      if (bootstrapAccount instanceof ContainerAccount) {
        ContainerAccount containerAccount = (ContainerAccount) bootstrapAccount;
        DockerRegistryAccountReverseIndex revIndex = new DockerRegistryAccountReverseIndex(providers);

        containerAccount.getDockerRegistries().forEach(reg -> {
          Set<Account> dependentAccounts = revIndex.get(reg.getAccountName());
          if (dependentAccounts == null || dependentAccounts.isEmpty()) {
            DockerRegistryAccount regAcct = (DockerRegistryAccount) accountService.getAnyProviderAccount(deploymentName, reg.getAccountName());
            ((DockerRegistryProvider) regAcct.getParent()).getAccounts().remove(regAcct);
          }
        });

        if (providers.getDockerRegistry().getAccounts().isEmpty()) {
          providers.getDockerRegistry().setEnabled(false);
        }
      }
    }
  }

  // Registry name -> Docker/DCOS accounts that use it.
  @Slf4j
  private static class DockerRegistryAccountReverseIndex extends HashMap<String, Set<Account>> {

    @SuppressWarnings("unchecked")
    DockerRegistryAccountReverseIndex(Providers providers) {
      super();

      NodeIterator providerNodes = providers.getChildren();
      Provider provider;
      while ((provider = (Provider) providerNodes.getNext()) != null) {
        for (Account a : (List<? extends Account>) provider.getAccounts()) {
          if (a instanceof ContainerAccount) {
            ContainerAccount account = (ContainerAccount) a;
            List<DockerRegistryReference> registries = account.getDockerRegistries();
            registries.forEach(reg -> this.computeIfAbsent(reg.getAccountName(), ignored -> new HashSet<>()).add(account));
          }
        }
      }
    }
  }
}
