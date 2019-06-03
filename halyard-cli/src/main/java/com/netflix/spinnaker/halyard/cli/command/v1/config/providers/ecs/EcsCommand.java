package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.ecs;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractNamedProviderCommand;

/** Interact with the ecs provider */
@Parameters(separators = "=")
public class EcsCommand extends AbstractNamedProviderCommand {
  protected String getProviderName() {
    return "ecs";
  }

  public EcsCommand() {
    super();
    registerSubcommand(new EcsAccountCommand());
  }
}
