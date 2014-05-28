package com.netflix.bluespar.kato.deploy.gce.description

import com.netflix.bluespar.kato.deploy.DeployDescription
import com.netflix.bluespar.kato.security.gce.GoogleCredentials

class BasicGoogleDeployDescription implements DeployDescription {
  String application
  String stack
  String image
  String type
  String zone
  GoogleCredentials credentials
}
