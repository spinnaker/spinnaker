package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.ecs;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractAccountCommand;

/** Interact with the aws provider's accounts */
@Parameters(separators = "=")
public class EcsAccountCommand extends AbstractAccountCommand {
  protected String getProviderName() {
    return "ecs";
  }

  public EcsAccountCommand() {
    super();
    registerSubcommand(new EcsAddAccountCommand());
    registerSubcommand(new EcsEditAccountCommand());
  }
}
