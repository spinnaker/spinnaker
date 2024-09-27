package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.cloudrun;

import static com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils.Format.STRING;

import com.beust.jcommander.Parameter;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractProviderCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.cloudrun.CloudrunProvider;
import lombok.AccessLevel;
import lombok.Getter;

public class CloudrunEditCommand extends AbstractProviderCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Edit Spinnaker's cloudrun configuration.";

  @Parameter(
      names = "--gcloudPath",
      description = "The path to the gcloud executable on the machine running clouddriver.")
  private String gcloudPath;

  protected String getProviderName() {
    return "cloudrun";
  }

  public CloudrunEditCommand() {}

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    String providerName = getProviderName();
    new OperationHandler<Provider>()
        .setFailureMesssage("Failed to get provider " + providerName + ".")
        .setSuccessMessage("Successfully got provider " + providerName + ".")
        .setFormat(STRING)
        .setUserFormatted(true)
        .setOperation(Daemon.getProvider(currentDeployment, providerName, !noValidate))
        .get();

    CloudrunProvider provider =
        (CloudrunProvider)
            new OperationHandler<Provider>()
                .setOperation(Daemon.getProvider(currentDeployment, providerName, !noValidate))
                .setFailureMesssage("Failed to get provider " + providerName + ".")
                .get();

    int originalHash = provider.hashCode();

    if (isSet(gcloudPath)) {
      provider.setGcloudPath(gcloudPath);
    }

    if (originalHash == provider.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to edit update cloudrun provider.")
        .setSuccessMessage("Successfully edited cloudrun provider.")
        .setOperation(
            Daemon.setProvider(currentDeployment, getProviderName(), !noValidate, provider))
        .get();
  }
}
