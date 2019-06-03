package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractNamedProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos.cluster.DCOSClusterCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;

/** Interact with the dcos provider */
@Parameters(separators = "=")
public class DCOSCommand extends AbstractNamedProviderCommand {
  protected String getProviderName() {
    return Provider.ProviderType.DCOS.getId();
  }

  public DCOSCommand() {
    super();
    registerSubcommand(new DCOSAccountCommand());
    registerSubcommand(new DCOSClusterCommand());
  }
}
