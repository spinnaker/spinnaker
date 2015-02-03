package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.FlexService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class ElasticIpService {
  private static final String GROUP = "elasticIps"

  @Autowired
  FlexService flexService

  List<Map> getForCluster(String application, String account, String cluster) {
    HystrixFactory.newListCommand(GROUP, "getElasticIpsForCluster", true) {
      flexService.getForCluster(application, account, cluster)
    } execute()
  }

  List<Map> getForAccount(String account) {
    HystrixFactory.newListCommand(GROUP, "getElasticIpsForAccount", true) {
      return flexService.getForAccount(account)
    } execute()
  }

  List<Map> getForAccountAndRegion(String account, String region) {
    HystrixFactory.newListCommand(GROUP, "getElasticIpsForAccountAndRegion", true) {
      return flexService.getForAccountAndRegion(account, region)
    } execute()
  }
}
