package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.openstack;

public class OpenstackCommandProperties {
  static final String AUTH_URL_DESCRIPTION = "The auth url of your cloud, usually found in the Horizon console under Compute > Access & Security > API Access > url for Identity. Must be Keystone v3";

  static final String USERNAME_DESCRIPTION = "The username used to access your cloud.";

  static final String PASSWORD_DESCRIPTION = "The password used to access your cloud.";

  static final String PROJECT_NAME_DESCRIPTION = "The name of the project (formerly tenant) within the cloud. Can be found in the RC file.";

  static final String DOMAIN_NAME_DESCRIPTION = "The domain of the cloud. Can be found in the RC file.";

  static final String REGIONS_DESCRIPTION = "The region(s) of the cloud. Can be found in the RC file.";

  static final String INSECURE_DESCRIPTION = "Disable certificate validation on SSL connections. Needed if certificates are self signed. Default false.";

  static final String USER_DATA_FILE_DESCRIPTION = "User data passed to Heat Orchestration Template. Replacement of tokens supported, see http://www.spinnaker.io/v1.0/docs/target-deployment-configuration#section-openstack for details.";

  static final String LBAAS_POLL_TIMEOUT_DESCRIPTION = "Time to stop polling octavia when a status of an entity does not change. Default 60.";

  static final String LBAAS_POLL_INTERVAL_DESCRIPTION = "Interval in seconds to poll octavia when an entity is created, updated, or deleted. Default 5.";
}
