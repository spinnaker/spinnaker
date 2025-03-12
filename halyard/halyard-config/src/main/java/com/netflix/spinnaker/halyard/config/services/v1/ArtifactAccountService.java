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
 *
 */

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactProvider;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the
 * current halconfig's deployments.
 */
@Component
public class ArtifactAccountService {
  @Autowired private LookupService lookupService;

  @Autowired private ArtifactProviderService artifactProviderService;

  @Autowired private ValidateService validateService;

  @Autowired private OptionsService optionsService;

  public List<ArtifactAccount> getAllArtifactAccounts(String deploymentName, String providerName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setArtifactProvider(providerName)
            .withAnyArtifactAccount();

    List<ArtifactAccount> matchingArtifactAccounts =
        lookupService.getMatchingNodesOfType(filter, ArtifactAccount.class);

    if (matchingArtifactAccounts.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No accounts could be found").build());
    } else {
      return matchingArtifactAccounts;
    }
  }

  private ArtifactAccount getArtifactAccount(NodeFilter filter, String accountName) {
    List<ArtifactAccount> matchingArtifactAccounts =
        lookupService.getMatchingNodesOfType(filter, ArtifactAccount.class);

    switch (matchingArtifactAccounts.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL, "No account with name \"" + accountName + "\" was found")
                .setRemediation(
                    "Check if this artifact account was defined in another provider, or create a new one")
                .build());
      case 1:
        return matchingArtifactAccounts.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL, "More than one account named \"" + accountName + "\" was found")
                .setRemediation(
                    "Manually delete/rename duplicate artifact accounts with name \""
                        + accountName
                        + "\" in your halconfig file")
                .build());
    }
  }

  public ArtifactAccount getArtifactProviderArtifactAccount(
      String deploymentName, String providerName, String accountName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setArtifactProvider(providerName)
            .setArtifactAccount(accountName);
    return getArtifactAccount(filter, accountName);
  }

  public ArtifactAccount getAnyArtifactProviderArtifactAccount(
      String deploymentName, String accountName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .withAnyArtifactProvider()
            .setArtifactAccount(accountName);
    return getArtifactAccount(filter, accountName);
  }

  public void setArtifactAccount(
      String deploymentName,
      String providerName,
      String accountName,
      ArtifactAccount newArtifactAccount) {
    ArtifactProvider provider =
        artifactProviderService.getArtifactProvider(deploymentName, providerName);

    for (int i = 0; i < provider.getAccounts().size(); i++) {
      ArtifactAccount account = (ArtifactAccount) provider.getAccounts().get(i);
      if (account.getNodeName().equals(accountName)) {
        provider.getAccounts().set(i, newArtifactAccount);
        return;
      }
    }

    throw new HalException(
        new ConfigProblemBuilder(
                Severity.FATAL, "Artifact account \"" + accountName + "\" wasn't found")
            .build());
  }

  public void deleteArtifactAccount(
      String deploymentName, String providerName, String accountName) {
    ArtifactProvider provider =
        artifactProviderService.getArtifactProvider(deploymentName, providerName);
    boolean removed =
        provider
            .getAccounts()
            .removeIf(account -> ((ArtifactAccount) account).getName().equals(accountName));

    if (!removed) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Severity.FATAL, "Artifact account \"" + accountName + "\" wasn't found")
              .build());
    }
  }

  public void addArtifactAccount(
      String deploymentName, String providerName, ArtifactAccount newArtifactAccount) {
    ArtifactProvider provider =
        artifactProviderService.getArtifactProvider(deploymentName, providerName);
    provider.getAccounts().add(newArtifactAccount);
  }

  public ProblemSet validateArtifactAccount(
      String deploymentName, String providerName, String accountName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setArtifactProvider(providerName)
            .setArtifactAccount(accountName);
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllArtifactAccounts(String deploymentName, String providerName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setArtifactProvider(providerName)
            .withAnyArtifactAccount();
    return validateService.validateMatchingFilter(filter);
  }
}
