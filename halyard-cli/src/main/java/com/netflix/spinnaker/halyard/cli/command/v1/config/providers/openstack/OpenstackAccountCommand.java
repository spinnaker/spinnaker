package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.openstack;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractAccountCommand;

/**
 * Interact with openstack provider's accounts
 */
@Parameters(separators = "=")
public class OpenstackAccountCommand extends AbstractAccountCommand {
  protected String getProviderName() {
    return "openstack";
  }

  public OpenstackAccountCommand() {
    super();
    registerSubcommand(new OpenstackAddAccountCommand());
    registerSubcommand(new OpenstackEditAccountCommand());
  }
}
