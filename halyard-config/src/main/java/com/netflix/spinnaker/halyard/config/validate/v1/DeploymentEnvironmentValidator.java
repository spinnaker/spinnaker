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

package com.netflix.spinnaker.halyard.config.validate.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment.DeploymentType;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.config.validate.v1.providers.kubernetes.KubernetesAccountValidator;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DeploymentEnvironmentValidator extends Validator<DeploymentEnvironment> {
  @Autowired AccountService accountService;

  @Autowired KubernetesAccountValidator kubernetesAccountValidator;

  @Override
  public void validate(ConfigProblemSetBuilder p, DeploymentEnvironment n) {

    log.info("[VALIDATE-COSTI] DeploymentEnvironmentValidator");

    DeploymentType type = n.getType();
    switch (type) {
      case LocalDebian:
      case BakeDebian:
        break;
      case Distributed:
        validateDistributedDeployment(p, n);
        break;
      case LocalGit:
        validateGitDeployment(p, n);
        break;
      default:
        throw new RuntimeException("Unknown deployment environment type " + type);
    }

    validateLivenessProbeConfig(p, n);
  }

  private void validateGitDeployment(ConfigProblemSetBuilder p, DeploymentEnvironment n) {
    if (StringUtils.isEmpty(n.getGitConfig().getOriginUser())) {
      p.addProblem(
              Problem.Severity.FATAL, "A git origin user must be supplied when deploying from git.")
          .setRemediation("Your github username is recommended.");
    }

    if (StringUtils.isEmpty(n.getGitConfig().getUpstreamUser())) {
      p.addProblem(
              Problem.Severity.FATAL,
              "A git upstream user must be supplied when deploying from git.")
          .setRemediation(
              "The user 'spinnaker' is recommended (unless you have a fork maintained by the org you develop under).");
    }
  }

  private void validateDistributedDeployment(ConfigProblemSetBuilder p, DeploymentEnvironment n) {
    String accountName = n.getAccountName();
    if (StringUtils.isEmpty(accountName)) {
      p.addProblem(
          Problem.Severity.FATAL,
          "An account name must be specified when using a Distributed deployment.");
      return;
    }

    DeploymentConfiguration deploymentConfiguration = n.parentOfType(DeploymentConfiguration.class);
    Account account;
    try {
      account =
          accountService.getAnyProviderAccount(
              deploymentConfiguration.getName(), n.getAccountName());
    } catch (ConfigNotFoundException e) {
      p.addProblem(Problem.Severity.FATAL, "Account " + accountName + " not defined.");
      return;
    }

    if (account instanceof GoogleAccount) {
      p.addProblem(
          Problem.Severity.WARNING,
          "Support for distributed deployments on GCE aren't fully supported yet.");
    } else if (account instanceof KubernetesAccount) {
      kubernetesAccountValidator.ensureKubectlExists(p);
    } else {
      p.addProblem(
          Problem.Severity.FATAL,
          "Account "
              + accountName
              + " is not in a provider that supports distributed installation of Spinnaker yet");
    }
  }

  private void validateLivenessProbeConfig(ConfigProblemSetBuilder p, DeploymentEnvironment n) {
    if (n.getLivenessProbeConfig() != null && n.getLivenessProbeConfig().isEnabled()) {
      if (n.getLivenessProbeConfig().getInitialDelaySeconds() == null) {
        p.addProblem(
            Problem.Severity.FATAL,
            "Setting `initialDelaySeconds` is required when enabling liveness probes. Use the --liveness-probe-initial-delay-seconds sub-command to set `initialDelaySeconds`.");
      }
    }
  }
}
