package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.cloudrun;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractNamedProviderCommand;

@Parameters(separators = "=")
public class CloudrunCommand extends AbstractNamedProviderCommand {
  protected String getProviderName() {
    return "cloudrun";
  }

  @Override
  protected String getLongDescription() {
    return String.join(
        "",
        "The Cloud Run provider is used to deploy resources to any number of Cloud Run applications. ",
        "To get started with Cloud Run, visit https://cloud.google.com/run/docs/. ",
        "For more information on how to configure individual accounts, please read the documentation ",
        "under `hal config provider cloudrun account -h`.");
  }

  public CloudrunCommand() {
    super();
    registerSubcommand(new CloudrunEditCommand());
    registerSubcommand(new CloudrunAccountCommand());
  }
}
