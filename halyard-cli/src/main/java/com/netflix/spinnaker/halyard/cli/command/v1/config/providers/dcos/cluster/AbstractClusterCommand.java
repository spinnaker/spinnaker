package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos.cluster;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractProviderCommand;

import java.util.ArrayList;
import java.util.List;

@Parameters(separators = "=")
public abstract class AbstractClusterCommand extends AbstractProviderCommand {
  @Parameter(description = "The name of the cluster to operate on.", arity = 1)
  List<String> clusters = new ArrayList<>();

  @Override
  public String getCommandName() {
    return "cluster";
  }

  @Override
  public String getDescription() {
    return "Manage and view Spinnaker configuration for the " + getProviderName() + " provider's cluster";
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
    switch (clusters.size()) {
    case 0:
      throw new IllegalArgumentException("No cluster name supplied");
    case 1:
      return clusters.get(0);
    default:
      throw new IllegalArgumentException("More than one cluster supplied");
    }
  }
}

