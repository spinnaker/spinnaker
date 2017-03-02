package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.openstack;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractNamedProviderCommand;

/**
 * Interact with the openstack provider
 */
@Parameters()
public class OpenstackCommand extends AbstractNamedProviderCommand {
  protected String getProviderName() {
    return "openstack";
  }

  public OpenstackCommand() {
    super();
    registerSubcommand(new OpenstackAccountCommand());
  }
}
