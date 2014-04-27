package com.netflix.kato.deploy.aws.description

import com.netflix.kato.deploy.DeployDescription
import groovy.transform.AutoClone

@AutoClone
class BasicAmazonDeployDescription extends AbstractAmazonCredentialsDescription implements DeployDescription {
  String application
  String amiName
  String clusterName
  String instanceType
  Map<String, List<String>> availabilityZones = [:]
  Capacity capacity = new Capacity()

  static class Capacity {
    int min
    int max
    int desired
  }
}
