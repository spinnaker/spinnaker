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

import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.errors.v1.config.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.errors.v1.config.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the current halconfig's
 * deployments.
 */
@Component
public class AccountService {
  @Autowired
  LookupService lookupService;

  @Autowired
  ProviderService providerService;

  @Autowired
  ValidateService validateService;

  public List<Account> getAllAccounts(NodeFilter filter) {
    filter = filter.withAnyHalconfigFile().withAnyAccount();

    List<Account> matchingAccounts = lookupService.getMatchingNodesOfType(filter, Account.class)
        .stream()
        .map(n -> (Account) n)
        .collect(Collectors.toList());

    if (matchingAccounts.size() == 0) {
      throw new ConfigNotFoundException(
          new ProblemBuilder(Problem.Severity.FATAL, "No accounts could be found")
              .setFilter(filter).build());
    } else {
      return matchingAccounts;
    }
  }

  public Account getAccount(NodeFilter filter) {
    String accountName = filter.getAccount();

    filter = filter.withAnyHalconfigFile();

    List<Account> matchingAccounts = lookupService.getMatchingNodesOfType(filter, Account.class)
        .stream()
        .map(n -> (Account) n)
        .collect(Collectors.toList());

    switch (matchingAccounts.size()) {
      case 0:
        throw new ConfigNotFoundException(new ProblemBuilder(
            Problem.Severity.FATAL, "No matching account with name \"" + accountName + "\" found")
            .setFilter(filter)
            .setRemediation("Check if this account was defined in another provider, or create a new one").build());
      case 1:
        return matchingAccounts.get(0);
      default:
        throw new IllegalConfigException(new ProblemBuilder(
            Problem.Severity.FATAL, "More than one matching account with name + \"" + accountName + "\" found")
            .setFilter(filter)
            .setRemediation("Manually delete/rename duplicate accounts with name \"" + accountName + "\" in your halconfig file").build());
    }
  }

  public void setAccount(NodeFilter filter, Account newAccount) {
    String accountName = filter.getAccount();

    Provider provider = providerService.getProvider(filter);

    for (int i = 0; i < provider.getAccounts().size(); i++) {
      Account account = (Account) provider.getAccounts().get(i);
      if (account.getNodeName().equals(accountName)) {
        provider.getAccounts().set(i, newAccount);
        return;
      }
    }

    throw new HalconfigException(new ProblemBuilder(Severity.FATAL, "Account \"" + accountName + "\" wasn't found").build());
  }

  public void deleteAccount(NodeFilter filter) {
    String accountName = filter.getAccount();

    Provider provider = providerService.getProvider(filter);
    boolean removed = provider.getAccounts().removeIf(account -> ((Account) account).getName().equals(accountName));

    if (!removed) {
      throw new HalconfigException(
          new ProblemBuilder(Severity.FATAL, "Account \"" + accountName + "\" wasn't found")
              .build());
    }
  }

  public void addAccount(NodeFilter filter, Account newAccount) {
    Provider provider = providerService.getProvider(filter);
    provider.getAccounts().add(newAccount);
  }

  public ProblemSet validateAccount(NodeFilter filter, Severity severity) {
    filter = filter.withAnyHalconfigFile();

    return validateService.validateMatchingFilter(filter, severity);
  }

  public ProblemSet validateAllAccounts(NodeFilter filter, Severity severity) {
    filter = filter.withAnyHalconfigFile().withAnyAccount();

    return validateService.validateMatchingFilter(filter, severity);
  }
}
