package com.netflix.spinnaker.halyard.cli.command.v1.config;

import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;

public class ListCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "list";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Lists all deployments";

  @Override
  protected void executeThis() {
    new OperationHandler<List<DeploymentConfiguration>>()
        .setFailureMesssage("Failed to get all deployments.")
        .setSuccessMessage("Retrieved all deployments.")
        .setFormat(AnsiFormatUtils.Format.YAML)
        .setUserFormatted(true)
        .setOperation(Daemon.getDeployments())
        .get();
  }
}
