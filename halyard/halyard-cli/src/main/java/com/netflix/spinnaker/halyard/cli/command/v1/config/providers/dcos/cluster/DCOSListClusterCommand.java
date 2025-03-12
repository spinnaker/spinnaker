package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos.cluster;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSCluster;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSProvider;
import java.util.List;
import lombok.Getter;

/** */
@Parameters(separators = "=")
public class DCOSListClusterCommand extends AbstractClusterCommand {
  public String getShortDescription() {
    return "List the cluster names for the " + getProviderName() + " provider.";
  }

  @Getter private String commandName = "list";

  private Provider getProvider() {
    String currentDeployment = getCurrentDeployment();
    String providerName = getProviderName();
    return new OperationHandler<Provider>()
        .setFailureMesssage("Failed to get provider " + providerName + ".")
        .setOperation(Daemon.getProvider(currentDeployment, providerName, !noValidate))
        .get();
  }

  @Override
  protected void executeThis() {
    Provider provider = getProvider();
    DCOSProvider dcosProvider = (DCOSProvider) provider;
    List<DCOSCluster> clusters = dcosProvider.getClusters();
    if (clusters.isEmpty()) {
      AnsiUi.success("No configured clusters for " + getProviderName() + ".");
    } else {
      AnsiUi.success("Clusters for " + getProviderName() + ":");
      clusters.forEach(cluster -> AnsiUi.listItem(cluster.getName()));
    }
  }

  @Override
  protected String getProviderName() {
    return Provider.ProviderType.DCOS.getId();
  }
}
