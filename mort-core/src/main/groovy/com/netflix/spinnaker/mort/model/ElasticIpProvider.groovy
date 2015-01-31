package com.netflix.spinnaker.mort.model

interface ElasticIpProvider<T extends ElasticIp> {
  Set<T> getAllByAccount(String account)
  Set<T> getAllByAccountAndRegion(String account, String region)
}
