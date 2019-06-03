package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos.cluster;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class DCOSDeleteClusterCommand extends AbstractClusterCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "delete";

  public String getShortDescription() {
    return "Delete a specific " + getProviderName() + " cluster by name.";
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String providerName = getProviderName();
    new OperationHandler<Void>()
        .setFailureMesssage(
            "Failed to delete cluster " + getClusterName() + " for provider " + providerName + ".")
        .setSuccessMessage(
            "Successfully deleted cluster "
                + getClusterName()
                + " for provider "
                + providerName
                + ".")
        .setOperation(
            Daemon.deleteCluster(currentDeployment, providerName, getClusterName(), !noValidate))
        .get();
  }

  @Override
  protected String getProviderName() {
    return Provider.ProviderType.DCOS.getId();
  }
}
