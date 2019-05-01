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

package com.netflix.spinnaker.halyard.config.services.v1.ci;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.CIAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.Ci;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.LookupService;
import com.netflix.spinnaker.halyard.config.services.v1.ValidateService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the current halconfigs
 * cis.
 */
@RequiredArgsConstructor
public abstract class CiService<T extends CIAccount, U extends Ci<T>> {
  protected final LookupService lookupService;
  private final ValidateService validateService;

  protected abstract List<T> getMatchingAccountNodes(NodeFilter filter);
  protected abstract List<U> getMatchingCiNodes(NodeFilter filter);
  public abstract String ciName();

  public U getCi(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setCi(ciName());

    List<U> matching = getMatchingCiNodes(filter);

    switch (matching.size()) {
      case 0:
        throw new ConfigNotFoundException(new ConfigProblemBuilder(Severity.FATAL,
            String.format("No Continuous Integration service with name '%s' could be found", ciName())).build());
      case 1:
        return matching.get(0);
      default:
        throw new IllegalConfigException(new ConfigProblemBuilder(Severity.FATAL,
            String.format("More than one CI with name '%s' found", ciName())).build());
    }
  }

  public List<U> getAllCis(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyCi();

    List<U> matching = getMatchingCiNodes(filter);

    if (matching.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No cis could be found")
              .build());
    } else {
      return matching;
    }
  }

  public void setEnabled(String deploymentName, boolean enabled) {
    Ci ci = getCi(deploymentName);
    ci.setEnabled(enabled);
  }

  public ProblemSet validateCi(String deploymentName) {
    NodeFilter filter = new NodeFilter()
        .setDeployment(deploymentName)
        .setCi(ciName())
        .withAnyAccount();

    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllCis(String deploymentName) {
    NodeFilter filter = new NodeFilter()
        .setDeployment(deploymentName)
        .withAnyCi()
        .withAnyAccount();

    return validateService.validateMatchingFilter(filter);
  }

  public List<T> getAllMasters(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setCi(ciName()).withAnyMaster();

    List<T> matchingAccounts = getMatchingAccountNodes(filter);

    if (matchingAccounts.size() == 0) {
      throw new ConfigNotFoundException(
              new ConfigProblemBuilder(Severity.FATAL, "No masters could be found").build());
    } else {
      return matchingAccounts;
    }
  }

  private T getMaster(NodeFilter filter, String masterName) {
    List<T> matchingAccounts = getMatchingAccountNodes(filter);

    switch (matchingAccounts.size()) {
      case 0:
        throw new ConfigNotFoundException(new ConfigProblemBuilder(
                Severity.FATAL, String.format("No master with name '%s' was found", masterName))
                .setRemediation("Check if this master was defined in another Continuous Integration service, or create a new one").build());
      case 1:
        return matchingAccounts.get(0);
      default:
        throw new IllegalConfigException(new ConfigProblemBuilder(
                Severity.FATAL, String.format("No master with name '%s' was found", masterName))
                .setRemediation(String.format("Manually delete/rename duplicate masters with name '%s' in your halconfig file", masterName)).build());
    }
  }

  public T getCiMaster(String deploymentName, String masterName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setCi(ciName()).setMaster(masterName);
    return getMaster(filter, masterName);
  }

  public T getAnyCiMaster(String deploymentName, String masterName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyCi().setMaster(masterName);
    return getMaster(filter, masterName);
  }

  public void setMaster(String deploymentName, String masterName, T newAccount) {
    U ci = getCi(deploymentName);

    for (int i = 0; i < ci.getMasters().size(); i++) {
      T account = ci.getMasters().get(i);
      if (account.getNodeName().equals(masterName)) {
        ci.getMasters().set(i, newAccount);
        return;
      }
    }

    throw new HalException(new ConfigProblemBuilder(Severity.FATAL, String.format("Master '%s' wasn't found", masterName)).build());
  }

  public void deleteMaster(String deploymentName, String masterName) {
    U ci = getCi(deploymentName);
    boolean removed = ci.getMasters().removeIf(master -> master.getName().equals(masterName));

    if (!removed) {
      throw new HalException(
              new ConfigProblemBuilder(Severity.FATAL, String.format("Master '%s' wasn't found", masterName))
                      .build());
    }
  }

  public void addMaster(String deploymentName, T newAccount) {
    U ci = getCi(deploymentName);
    ci.getMasters().add(newAccount);
  }

  public ProblemSet validateMaster(String deploymentName, String masterName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setCi(ciName()).setMaster(masterName);
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllMasters(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setCi(ciName()).withAnyMaster();
    return validateService.validateMatchingFilter(filter);
  }
}
