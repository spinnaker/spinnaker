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

package com.netflix.spinnaker.halyard.cli.command.v1.config;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.DeploymentTypeConverter;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment.DeploymentType;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class EditDeploymentEnvironmentCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  final private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  final private String description = "Edit Spinnaker's deployment footprint and configuration.";

  @Parameter(
      names = "--account-name",
      description = "The Spinnaker account that Spinnaker will be deployed to, assuming you are running"
          + "a deployment of Spinnaker that requires an active cloud provider."
  )
  private String accountName;

  @Parameter(
      names = "--bootstrap-only",
      description = "A bootstrap-only account is the account in which Spinnaker itself is deployed. " +
          "When true, this account will not be included the accounts managed by Spinnaker."
  )
  Boolean bootstrapOnly;

  @Parameter(
      names = "--update-versions",
      description = "When set to \"false\", any *local* version of Spinnaker components will be used instead of attempting " +
          "to update. This does not work for distributed installations of Spinnaker, where no *local* version exists."
  )
  Boolean updateVersions;

  @Parameter(
      names = "--type",
      description = "Distributed: Deploy Spinnaker with one server group per microservice, and a single shared Redis.\n"
          + "LocalDebian: Download and run the Spinnaker debians on the machine running the Daemon.\n"
          + "LocalGit: Download and run the Spinnaker git repos on the machine running the Daemon.",
      converter = DeploymentTypeConverter.class
  )
  private DeploymentType type;

  @Parameter(
      names = "--consul-enabled",
      arity = 1,
      description = "Whether or not to use Consul as a service discovery mechanism to deploy Spinnaker."
  )
  private Boolean consulEnabled;

  @Parameter(
      names = "--consul-address",
      description = "The address of a running Consul cluster. See https://www.consul.io/.\n"
          + "This is only required when Spinnaker is being deployed in non-Kubernetes clustered configuration."
  )
  private String consulAddress;

  @Parameter(
      names = "--vault-enabled",
      arity = 1,
      description = "Whether or not to use Vault as a secret storage mechanism to deploy Spinnaker."
  )
  private Boolean vaultEnabled;

  @Parameter(
      names = "--vault-address",
      description = "The address of a running Vault datastore. See https://www.vaultproject.io/."
          + "This is only required when Spinnaker is being deployed in non-Kubernetes clustered configuration."
  )
  private String vaultAddress;

  @Parameter(
      names = "--location",
      description = "This is the location spinnaker will be deployed to. When deploying to "
          + "Kubernetes, use this flag to specify the namespace to deploy to (defaults to 'spinnaker')"
  )
  private String location;

  @Parameter(
      names = "--git-upstream-user",
      description = "This is the upstream git user you are configuring to pull changes from & push PRs to."
  )
  private String gitUpstreamUser;

  @Parameter(
      names = "--git-origin-user",
      description = "This is the git user your github fork exists under."
  )
  private String gitOriginUser;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    DeploymentEnvironment deploymentEnvironment = new OperationHandler<DeploymentEnvironment>()
        .setFailureMesssage("Failed to get your deployment environment.")
        .setOperation(Daemon.getDeploymentEnvironment(currentDeployment, false))
        .get();

    int originalHash = deploymentEnvironment.hashCode();

    DeploymentEnvironment.GitConfig gitConfig = deploymentEnvironment.getGitConfig();
    if (gitConfig == null) {
      gitConfig = new DeploymentEnvironment.GitConfig();
    }

    gitConfig.setOriginUser(isSet(gitOriginUser) ? gitOriginUser : gitConfig.getOriginUser());
    gitConfig.setUpstreamUser(isSet(gitUpstreamUser) ? gitUpstreamUser : gitConfig.getUpstreamUser());
    deploymentEnvironment.setGitConfig(gitConfig);

    DeploymentEnvironment.Consul consul = deploymentEnvironment.getConsul();
    if (consul == null) {
      consul = new DeploymentEnvironment.Consul();
    }

    DeploymentEnvironment.Vault vault = deploymentEnvironment.getVault();
    if (vault == null) {
      vault = new DeploymentEnvironment.Vault();
    }

    deploymentEnvironment.setAccountName(isSet(accountName) ? accountName : deploymentEnvironment.getAccountName());
    deploymentEnvironment.setBootstrapOnly(isSet(bootstrapOnly) ? bootstrapOnly : deploymentEnvironment.getBootstrapOnly());
    deploymentEnvironment.setUpdateVersions(isSet(updateVersions) ? updateVersions : deploymentEnvironment.getUpdateVersions());
    deploymentEnvironment.setType(type != null ? type : deploymentEnvironment.getType());

    consul.setAddress(isSet(consulAddress) ? consulAddress : consul.getAddress());
    consul.setEnabled(isSet(consulEnabled) ? consulEnabled : consul.isEnabled());
    deploymentEnvironment.setConsul(consul);

    vault.setAddress(isSet(vaultAddress) ? vaultAddress : vault.getAddress());
    vault.setEnabled(isSet(vaultEnabled) ? vaultEnabled : vault.isEnabled());
    deploymentEnvironment.setVault(vault);

    deploymentEnvironment.setLocation(isSet(location) ? location : deploymentEnvironment.getLocation());

    if (originalHash == deploymentEnvironment.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to update your deployment environment.")
        .setSuccessMessage("Successfully updated your deployment environment.")
        .setOperation(Daemon.setDeploymentEnvironment(currentDeployment, !noValidate, deploymentEnvironment))
        .get();
  }
}
