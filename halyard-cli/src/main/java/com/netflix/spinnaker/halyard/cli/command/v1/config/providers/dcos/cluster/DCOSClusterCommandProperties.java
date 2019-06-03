package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos.cluster;

public class DCOSClusterCommandProperties {
  public static final String DCOS_URL_DESCRIPTION =
      "URL of the endpoint for the DC/OS cluster's admin router.";
  public static final String CA_CERT_FILE_DESCRIPTION =
      "Root certificate file to trust for connections to the cluster";
  public static final String SKIP_TLS_VERIFY_DESCRIPTION =
      "Set this flag to disable verification of certificates from the cluster (insecure)";
  public static final String LOADBALANCER_IMAGE_DESCRIPTION =
      "Marathon-lb image to use when creating a load balancer with Spinnaker";
  public static final String LOADBALANCER_SECRET_DESCRIPTION =
      "Name of the secret to use for allowing marathon-lb to authenticate with the cluster.  Only necessary for clusters with strict or permissive security.";
}
