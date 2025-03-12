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
 */

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the
 * current halconfig deployment's canary configurations.
 */
@Component
public class CanaryAccountService {

  @Autowired private CanaryService canaryService;

  @Autowired private OptionsService optionsService;

  public AbstractCanaryAccount getCanaryAccount(
      String deploymentName, String serviceIntegrationName, String accountName) {
    AbstractCanaryServiceIntegration serviceIntegration =
        getServiceIntegration(deploymentName, serviceIntegrationName);
    List<AbstractCanaryAccount> matchingAccounts =
        (List<AbstractCanaryAccount>)
            serviceIntegration.getAccounts().stream()
                .filter(a -> (((AbstractCanaryAccount) a).getName().equals(accountName)))
                .collect(Collectors.toList());

    switch (matchingAccounts.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL, "No account with name \"" + accountName + "\" was found")
                .setRemediation(
                    "Check if this account was defined in another service integration, or create a new one")
                .build());
      case 1:
        return matchingAccounts.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL, "More than one account named \"" + accountName + "\" was found")
                .setRemediation(
                    "Manually delete/rename duplicate canary accounts with name \""
                        + accountName
                        + "\" in your halconfig file")
                .build());
    }
  }

  private AbstractCanaryServiceIntegration getServiceIntegration(
      String deploymentName, String serviceIntegrationName) {
    Canary canary = canaryService.getCanary(deploymentName);
    return canary.getServiceIntegrations().stream()
        .filter(s -> s.getName().equals(serviceIntegrationName))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Canary service integration " + serviceIntegrationName + " not found."));
  }

  public void setAccount(
      String deploymentName,
      String serviceIntegrationName,
      String accountName,
      AbstractCanaryAccount newAccount) {
    AbstractCanaryServiceIntegration serviceIntegration =
        getServiceIntegration(deploymentName, serviceIntegrationName);

    for (int i = 0; i < serviceIntegration.getAccounts().size(); i++) {
      AbstractCanaryAccount account =
          (AbstractCanaryAccount) serviceIntegration.getAccounts().get(i);

      if (account.getNodeName().equals(accountName)) {
        serviceIntegration.getAccounts().set(i, newAccount);
        return;
      }
    }

    throw new HalException(
        new ConfigProblemBuilder(
                Severity.FATAL, "Canary account \"" + accountName + "\" wasn't found")
            .build());
  }

  public void deleteAccount(
      String deploymentName, String serviceIntegrationName, String accountName) {
    AbstractCanaryServiceIntegration serviceIntegration =
        getServiceIntegration(deploymentName, serviceIntegrationName);
    boolean removed =
        serviceIntegration
            .getAccounts()
            .removeIf(account -> ((AbstractCanaryAccount) account).getName().equals(accountName));

    if (!removed) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Severity.FATAL, "Canary account \"" + accountName + "\" wasn't found")
              .build());
    }
  }

  public void addAccount(
      String deploymentName,
      String serviceIntegrationName,
      AbstractCanaryAccount newCanaryAccount) {
    AbstractCanaryServiceIntegration serviceIntegration =
        getServiceIntegration(deploymentName, serviceIntegrationName);
    serviceIntegration.getAccounts().add(newCanaryAccount);
  }

  public OptionsService.FieldOptions getAccountOptions(
      String deploymentName, String providerName, String accountName, String fieldName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setProvider(providerName)
            .setAccount(accountName);
    return optionsService.options(filter, Account.class, fieldName);
  }
}
