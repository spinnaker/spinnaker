package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos.cluster;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;

/** Interact with the DC/OS provider's clusters */
@Parameters(separators = "=")
public class DCOSClusterCommand extends AbstractClusterCommand {
  protected String getProviderName() {
    return Provider.ProviderType.DCOS.getId();
  }

  public DCOSClusterCommand() {
    super();
    registerSubcommand(new DCOSAddClusterCommand());
    registerSubcommand(new DCOSEditClusterCommand());
    registerSubcommand(new DCOSDeleteClusterCommand());
    registerSubcommand(new DCOSGetClusterCommand());
    registerSubcommand(new DCOSListClusterCommand());
  }
}
