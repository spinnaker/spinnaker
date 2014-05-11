package com.netflix.kato.deploy.gce.description

import com.netflix.kato.security.gce.GoogleCredentials
import com.netflix.kato.deploy.DeployDescription

class BasicGoogleDeployDescription implements DeployDescription {
  String application
  String stack
  String image
  String type
  String zone
  GoogleCredentials credentials
}
