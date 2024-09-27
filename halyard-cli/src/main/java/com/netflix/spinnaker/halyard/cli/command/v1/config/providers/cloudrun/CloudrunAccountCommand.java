package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.cloudrun;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractAccountCommand;

@Parameters(separators = "=")
public class CloudrunAccountCommand extends AbstractAccountCommand {
  protected String getProviderName() {
    return "cloudrun";
  }

  @Override
  protected String getLongDescription() {
    return String.join(
        "",
        "An account in the Cloud Run provider refers to a single Cloud Run application. ",
        "Spinnaker assumes that your Cloud Run application already exists. ",
        "You can create an application in your Google Cloud Platform project by running ",
        "`gcloud app create --region <region>`.");
  }

  public CloudrunAccountCommand() {
    super();
    registerSubcommand(new CloudrunAddAccountCommand());
    registerSubcommand(new CloudrunEditAccountCommand());
  }
}
