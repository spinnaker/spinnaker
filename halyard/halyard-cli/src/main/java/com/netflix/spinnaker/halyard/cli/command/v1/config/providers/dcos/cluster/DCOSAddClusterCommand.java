package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos.cluster;

import static java.util.Objects.nonNull;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSCluster;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class DCOSAddClusterCommand extends AbstractClusterCommand {

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "add";

  protected String getProviderName() {
    return Provider.ProviderType.DCOS.getId();
  }

  @Parameter(
      names = "--dcos-url",
      description = DCOSClusterCommandProperties.DCOS_URL_DESCRIPTION,
      required = true)
  String dcosUrl;

  @Parameter(
      names = "--ca-cert-file",
      converter = LocalFileConverter.class,
      description = DCOSClusterCommandProperties.CA_CERT_FILE_DESCRIPTION)
  String caCertFile;

  @Parameter(
      names = "--skip-tls-verify",
      description = DCOSClusterCommandProperties.SKIP_TLS_VERIFY_DESCRIPTION)
  Boolean insecureSkipTlsVerify;

  @Parameter(
      names = "--lb-image",
      description = DCOSClusterCommandProperties.LOADBALANCER_IMAGE_DESCRIPTION)
  String loadBalancerImage;

  @Parameter(
      names = "--lb-account-secret",
      description = DCOSClusterCommandProperties.LOADBALANCER_SECRET_DESCRIPTION)
  String loadBalancerServiceAccountSecret;

  @Override
  protected void executeThis() {

    DCOSCluster cluster = new DCOSCluster();
    cluster
        .setName(getClusterName())
        .setDcosUrl(dcosUrl)
        .setCaCertFile(caCertFile)
        .setInsecureSkipTlsVerify(insecureSkipTlsVerify);

    if (nonNull(loadBalancerImage)) {
      final DCOSCluster.LoadBalancer loadBalancer =
          new DCOSCluster.LoadBalancer()
              .setImage(loadBalancerImage)
              .setServiceAccountSecret(loadBalancerServiceAccountSecret);
      cluster.setLoadBalancer(loadBalancer);
    }

    new OperationHandler<Void>()
        .setFailureMesssage(
            "Failed to add cluster "
                + getClusterName()
                + " for provider "
                + getProviderName()
                + ".")
        .setSuccessMessage(
            "Successfully added cluster "
                + getClusterName()
                + " for provider "
                + getProviderName()
                + ".")
        .setOperation(
            Daemon.addCluster(getCurrentDeployment(), getProviderName(), !noValidate, cluster))
        .get();
  }
}
