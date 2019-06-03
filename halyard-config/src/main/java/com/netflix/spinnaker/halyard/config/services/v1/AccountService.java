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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
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
public class AccountService {
  @Autowired private LookupService lookupService;

  @Autowired private ProviderService providerService;

  @Autowired private ValidateService validateService;

  @Autowired private OptionsService optionsService;

  public List<Account> getAllAccounts(String deploymentName, String providerName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setProvider(providerName).withAnyAccount();

    List<Account> matchingAccounts = lookupService.getMatchingNodesOfType(filter, Account.class);

    if (matchingAccounts.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No accounts could be found").build());
    } else {
      return matchingAccounts;
    }
  }

  private Account getAccount(NodeFilter filter, String accountName) {
    List<Account> matchingAccounts = lookupService.getMatchingNodesOfType(filter, Account.class);

    switch (matchingAccounts.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL, "No account with name \"" + accountName + "\" was found")
                .setRemediation(
                    "Check if this account was defined in another provider, or create a new one")
                .build());
      case 1:
        return matchingAccounts.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL, "More than one account named \"" + accountName + "\" was found")
                .setRemediation(
                    "Manually delete/rename duplicate accounts with name \""
                        + accountName
                        + "\" in your halconfig file")
                .build());
    }
  }

  public Account getProviderAccount(
      String deploymentName, String providerName, String accountName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setProvider(providerName)
            .setAccount(accountName);
    return getAccount(filter, accountName);
  }

  public Account getAnyProviderAccount(String deploymentName, String accountName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).withAnyProvider().setAccount(accountName);
    return getAccount(filter, accountName);
  }

  public void setAccount(
      String deploymentName, String providerName, String accountName, Account newAccount) {
    Provider provider = providerService.getProvider(deploymentName, providerName);

    for (int i = 0; i < provider.getAccounts().size(); i++) {
      Account account = (Account) provider.getAccounts().get(i);
      if (account.getNodeName().equals(accountName)) {
        provider.getAccounts().set(i, newAccount);
        return;
      }
    }

    throw new HalException(
        new ConfigProblemBuilder(Severity.FATAL, "Account \"" + accountName + "\" wasn't found")
            .build());
  }

  public void deleteAccount(String deploymentName, String providerName, String accountName) {
    Provider provider = providerService.getProvider(deploymentName, providerName);
    boolean removed =
        provider
            .getAccounts()
            .removeIf(account -> ((Account) account).getName().equals(accountName));

    if (!removed) {
      throw new HalException(
          new ConfigProblemBuilder(Severity.FATAL, "Account \"" + accountName + "\" wasn't found")
              .build());
    }
  }

  public void addAccount(String deploymentName, String providerName, Account newAccount) {
    Provider provider = providerService.getProvider(deploymentName, providerName);
    provider.getAccounts().add(newAccount);
  }

  public ProblemSet validateAccount(
      String deploymentName, String providerName, String accountName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setProvider(providerName)
            .setAccount(accountName);
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllAccounts(String deploymentName, String providerName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setProvider(providerName).withAnyAccount();
    return validateService.validateMatchingFilter(filter);
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
