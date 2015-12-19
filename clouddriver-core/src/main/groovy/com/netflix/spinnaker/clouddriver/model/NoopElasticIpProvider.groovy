package com.netflix.spinnaker.clouddriver.model

class NoopElasticIpProvider implements ElasticIpProvider {
  @Override
  Set getAllByAccount(String account) {
    Collections.emptySet()
  }

  @Override
  Set getAllByAccountAndRegion(String account, String region) {
    Collections.emptySet()
  }
}
