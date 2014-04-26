package com.netflix.kato.deploy.aws.description

class ShrinkClusterDescription extends AbstractAmazonCredentialsDescription {
  String application
  String clusterName
  Boolean forceDelete = Boolean.TRUE
  List<String> regions
}
