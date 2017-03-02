package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.openstack;

import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractAccountCommand;

/**
 * Interact with openstack provider's accounts
 */
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
