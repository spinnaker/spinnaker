package com.netflix.spinnaker.kato.cf.deploy.description

import com.netflix.spinnaker.kato.cf.security.CloudFoundryAccountCredentials

class DestroyCloudFoundryServerGroupDescription {
  String serverGroupName
  String zone
  String getAccountName() {
    credentials?.name
  }
  CloudFoundryAccountCredentials credentials
}
