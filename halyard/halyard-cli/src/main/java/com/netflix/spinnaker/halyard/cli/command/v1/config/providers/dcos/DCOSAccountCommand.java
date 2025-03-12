package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;

/** Interact with the DC/OS provider's accounts */
@Parameters(separators = "=")
public class DCOSAccountCommand extends AbstractAccountCommand {
  protected String getProviderName() {
    return Provider.ProviderType.DCOS.getId();
  }

  public DCOSAccountCommand() {
    super();
    registerSubcommand(new DCOSAddAccountCommand());
    registerSubcommand(new DCOSEditAccountCommand());
  }
}
