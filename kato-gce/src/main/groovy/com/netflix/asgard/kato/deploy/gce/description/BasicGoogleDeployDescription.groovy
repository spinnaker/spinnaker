package com.netflix.asgard.kato.deploy.gce.description

import com.netflix.asgard.kato.deploy.DeployDescription
import com.netflix.asgard.kato.security.gce.GoogleCredentials

class BasicGoogleDeployDescription implements DeployDescription {
  String application
  String stack
  String image
  String type
  String zone
  GoogleCredentials credentials
}
