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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.ci.codebuild.AwsCodeBuild;
import com.netflix.spinnaker.halyard.config.model.v1.node.CIAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.Ci;
import com.netflix.spinnaker.halyard.config.model.v1.node.Cis;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.config.services.v1.LookupService;
import com.netflix.spinnaker.halyard.config.services.v1.ValidateService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the
 * current halconfigs cis.
 */
@RequiredArgsConstructor
public abstract class CiService<T extends CIAccount, U extends Ci<T>> {
  protected final LookupService lookupService;
  protected final ObjectMapper objectMapper;
  private final ValidateService validateService;
  private final DeploymentService deploymentService;

  @Component
  @RequiredArgsConstructor
  public static class Members {
    private final LookupService lookupService;
    private final ValidateService validateService;
    private final DeploymentService deploymentService;
    private final ObjectMapper objectMapper = new ObjectMapper();
  }

  public CiService(Members members) {
    this.lookupService = members.lookupService;
    this.validateService = members.validateService;
    this.deploymentService = members.deploymentService;
    this.objectMapper = members.objectMapper;
  }

  public abstract T convertToAccount(Object object);

  protected abstract List<T> getMatchingAccountNodes(NodeFilter filter);

  protected abstract List<U> getMatchingCiNodes(NodeFilter filter);

  public abstract String ciName();

  public U getCi(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setCi(ciName());

    List<U> matching = getMatchingCiNodes(filter);

    switch (matching.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL,
                    String.format(
                        "No Continuous Integration service with name '%s' could be found",
                        ciName()))
                .build());
      case 1:
        return matching.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL,
                    String.format("More than one CI with name '%s' found", ciName()))
                .build());
    }
  }

  public void setCi(String deploymentName, Ci ci) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    Cis cis = deploymentConfiguration.getCi();
    switch (ci.getNodeName()) {
      case "codebuild":
        cis.setCodebuild((AwsCodeBuild) ci);
        break;
      default:
        throw new IllegalArgumentException(
            "SetCi is not supported by ci provider " + ci.getNodeName());
    }
  }

  public void setEnabled(String deploymentName, boolean enabled) {
    Ci ci = getCi(deploymentName);
    ci.setEnabled(enabled);
  }

  public ProblemSet validateCi(String deploymentName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setCi(ciName()).withAnyAccount();

    return validateService.validateMatchingFilter(filter);
  }

  public List<T> getAllMasters(String deploymentName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setCi(ciName()).withAnyMaster();

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
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL, String.format("No master with name '%s' was found", masterName))
                .setRemediation(
                    "Check if this master was defined in another Continuous Integration service, or create a new one")
                .build());
      case 1:
        return matchingAccounts.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL, String.format("No master with name '%s' was found", masterName))
                .setRemediation(
                    String.format(
                        "Manually delete/rename duplicate masters with name '%s' in your halconfig file",
                        masterName))
                .build());
    }
  }

  public T getCiMaster(String deploymentName, String masterName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setCi(ciName()).setMaster(masterName);
    return getMaster(filter, masterName);
  }

  public void setMaster(String deploymentName, String masterName, T newAccount) {
    U ci = getCi(deploymentName);

    for (int i = 0; i < ci.listAccounts().size(); i++) {
      T account = ci.listAccounts().get(i);
      if (account.getNodeName().equals(masterName)) {
        ci.listAccounts().set(i, newAccount);
        return;
      }
    }

    throw new HalException(
        new ConfigProblemBuilder(
                Severity.FATAL, String.format("Master '%s' wasn't found", masterName))
            .build());
  }

  public void deleteMaster(String deploymentName, String masterName) {
    U ci = getCi(deploymentName);
    boolean removed = ci.listAccounts().removeIf(master -> master.getName().equals(masterName));

    if (!removed) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Severity.FATAL, String.format("Master '%s' wasn't found", masterName))
              .build());
    }
  }

  public void addMaster(String deploymentName, T newAccount) {
    U ci = getCi(deploymentName);
    ci.listAccounts().add(newAccount);
  }

  public ProblemSet validateMaster(String deploymentName, String masterName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setCi(ciName()).setMaster(masterName);
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllMasters(String deploymentName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setCi(ciName()).withAnyMaster();
    return validateService.validateMatchingFilter(filter);
  }
}
