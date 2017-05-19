package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos.cluster;

import lombok.AccessLevel;
import lombok.Getter;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Cluster;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSCluster;

import java.util.Objects;

import static com.beust.jcommander.Strings.isStringEmpty;

@Parameters(separators = "=")
public class DCOSEditClusterCommand extends AbstractClusterCommand {
  protected String getProviderName() {
    return Provider.ProviderType.DCOS.getId();
  }


  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Parameter(
      names = "--dcos-url",
      description = DCOSClusterCommandProperties.DCOS_URL_DESCRIPTION
  )
  String dcosUrl;

  //TODO(willgorman): might be good to support loading from a file
  @Parameter(
      names = "--ca-cert-data",
      description = DCOSClusterCommandProperties.CA_CERT_DATA_DESCRIPTION
  )
  String caCertData;

  @Parameter(
      names = "--remove-ca-cert-data",
      description = "Remove the CA certificate for this cluster"
  )
  Boolean removeCaCertData = false;

  @Parameter(
      names = "--skip-tls-verify",
      description = DCOSClusterCommandProperties.SKIP_TLS_VERIFY_DESCRIPTION
  )
  Boolean insecureSkipTlsVerify;

  @Parameter(
      names = "--lb-image",
      description = DCOSClusterCommandProperties.LOADBALANCER_IMAGE_DESCRIPTION
  )
  String loadBalancerImage;

  @Parameter(
      names = "--lb-account-secret",
      description = DCOSClusterCommandProperties.LOADBALANCER_SECRET_DESCRIPTION
  )
  String loadBalancerServiceAccountSecret;

  @Parameter(
      names = "--remove-lb",
      description = "Remove the load balancer attributes for this cluster"
  )
  Boolean removeLoadBalancer = false;

  @Override
  protected void executeThis() {
    String clusterName = getClusterName();
    String providerName = getProviderName();
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    DCOSCluster cluster = (DCOSCluster) new OperationHandler<Cluster>()
        .setFailureMesssage("Failed to get cluster " + clusterName + " for provider " + providerName + ".")
        .setOperation(Daemon.getCluster(currentDeployment, providerName, clusterName, false))
        .get();

    int originalHash = cluster.hashCode();

    if (!isStringEmpty(dcosUrl)) {
      cluster.setDcosUrl(dcosUrl);
    }

    if (!isStringEmpty(caCertData)) {
      cluster.setCaCertData(caCertData);
    }

    if (removeCaCertData) {
      cluster.setCaCertData(null);
    }

    if (Objects.nonNull(insecureSkipTlsVerify)) {
      cluster.setInsecureSkipTlsVerify(insecureSkipTlsVerify);
    }

    if (!isStringEmpty(loadBalancerImage)) {
      DCOSCluster.LoadBalancer loadBalancer = cluster.getLoadBalancer();
      if (loadBalancer == null) {
        loadBalancer = new DCOSCluster.LoadBalancer();
        cluster.setLoadBalancer(loadBalancer);
      }
      loadBalancer.setImage(loadBalancerImage);
    }

    if (!isStringEmpty(loadBalancerServiceAccountSecret)) {
      DCOSCluster.LoadBalancer loadBalancer = cluster.getLoadBalancer();
      if (loadBalancer == null) {
        loadBalancer = new DCOSCluster.LoadBalancer();
        cluster.setLoadBalancer(loadBalancer);
      }
      loadBalancer.setServiceAccountSecret(loadBalancerServiceAccountSecret);
    }

    if (removeLoadBalancer) {
      cluster.setLoadBalancer(null);
    }

    if (originalHash == cluster.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to edit cluster " + clusterName + " for provider " + providerName + ".")
        .setSuccessMessage("Successfully edited cluster " + clusterName + " for provider " + providerName + ".")
        .setOperation(Daemon.setCluster(currentDeployment, providerName, clusterName, !noValidate, cluster))
        .get();
  }
}
