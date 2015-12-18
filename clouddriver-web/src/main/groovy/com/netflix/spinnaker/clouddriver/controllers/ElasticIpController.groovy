package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.ElasticIp
import com.netflix.spinnaker.clouddriver.model.ElasticIpProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/elasticIps")
@RestController
class ElasticIpController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  List<ElasticIpProvider> elasticIpProviders

  @RequestMapping(method = RequestMethod.GET, value = "/{account}")
  Set<ElasticIp> listByAccount(@PathVariable String account) {
    rx.Observable.from(elasticIpProviders).flatMap {
      rx.Observable.from(it.getAllByAccount(account))
    } reduce(new HashSet<ElasticIp>(), { Set elasticIps, ElasticIp elasticIp ->
      elasticIps << elasticIp
      elasticIps
    }) toBlocking() first()
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{account}", params = ['region'])
  Set<ElasticIp> listByAccountAndRegion(@PathVariable String account, @RequestParam("region") String region) {
    rx.Observable.from(elasticIpProviders).flatMap {
      rx.Observable.from(it.getAllByAccountAndRegion(account, region))
    } reduce(new HashSet<ElasticIp>(), { Set elasticIps, ElasticIp elasticIp ->
      elasticIps << elasticIp
      elasticIps
    }) toBlocking() first()
  }
}
