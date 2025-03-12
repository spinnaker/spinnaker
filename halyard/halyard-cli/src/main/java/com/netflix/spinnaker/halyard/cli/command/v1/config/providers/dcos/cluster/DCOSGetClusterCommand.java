package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos.cluster;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Cluster;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSCluster;
import lombok.Getter;

@Parameters(separators = "=")
public class DCOSGetClusterCommand extends AbstractClusterCommand {
  public String getShortDescription() {
    return "Get the specified cluster details for the " + getProviderName() + " provider.";
  }

  @Getter private String commandName = "get";

  @Override
  protected void executeThis() {
    AnsiUi.success(AnsiFormatUtils.format(getCluster(getClusterName())));
  }

  private DCOSCluster getCluster(String clusterName) {
    String currentDeployment = getCurrentDeployment();
    String providerName = getProviderName();
    return (DCOSCluster)
        new OperationHandler<Cluster>()
            .setFailureMesssage(
                "Failed to get cluster " + clusterName + " for provider " + providerName + ".")
            .setSuccessMessage("Cluster " + clusterName + ": ")
            .setFormat(AnsiFormatUtils.Format.STRING)
            .setUserFormatted(true)
            .setOperation(Daemon.getCluster(currentDeployment, providerName, clusterName, false))
            .get();
  }

  @Override
  protected String getProviderName() {
    return Provider.ProviderType.DCOS.getId();
  }
}
