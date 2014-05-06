package com.netflix.kato.deploy.aws.description

class ResizeAsgDescription extends AbstractAmazonCredentialsDescription {
  String asgName
  List<String> regions
  Capacity capacity = new Capacity()

  static class Capacity {
    int min
    int max
    int desired
  }
}
