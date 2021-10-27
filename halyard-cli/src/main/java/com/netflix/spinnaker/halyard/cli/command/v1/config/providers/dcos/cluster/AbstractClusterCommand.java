package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos.cluster;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractProviderCommand;

@Parameters(separators = "=")
public abstract class AbstractClusterCommand extends AbstractProviderCommand {
  @Parameter(description = "The name of the cluster to operate on.")
  String cluster;

  @Override
  public String getCommandName() {
    return "cluster";
  }

  @Override
  public String getShortDescription() {
    return "Manage and view Spinnaker configuration for the "
        + getProviderName()
        + " provider's cluster";
  }

  @Override
  protected void executeThis() {
    showHelp();
  }

  @Override
  public String getMainParameter() {
    return "cluster";
  }

  public String getClusterName(String defaultName) {
    try {
      return getClusterName();
    } catch (IllegalArgumentException e) {
      return defaultName;
    }
  }

  public String getClusterName() {
    if (cluster == null) {
      throw new IllegalArgumentException("No cluster name supplied");
    }
    return cluster;
  }
}
