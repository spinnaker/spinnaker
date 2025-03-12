package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos;

public class DCOSCommandProperties {
  public static final String DOCKER_REGISTRIES_DESCRIPTION =
      "Provide the list of docker registries to use with this DC/OS account";
  public static final String USER_CREDENTIAL =
      "A DC/OS cluster user credential in 3 parts: cluster-name uid password";
  public static final String SERVICE_CREDENTIAL =
      "A DC/OS cluster service account credential in 3 parts: cluster-name uid serviceKey";
}
