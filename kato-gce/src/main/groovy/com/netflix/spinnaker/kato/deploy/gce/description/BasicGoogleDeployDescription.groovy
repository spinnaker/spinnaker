package com.netflix.spinnaker.kato.deploy.gce.description

import com.netflix.spinnaker.kato.deploy.DeployDescription
import com.netflix.spinnaker.kato.security.gce.GoogleCredentials

class BasicGoogleDeployDescription implements DeployDescription {
  String application
  String stack
  String image
  String type
  String zone
  GoogleCredentials credentials
}
