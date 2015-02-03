package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.ElasticIpService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RequestMapping("/elasticIps/{account}")
@RestController
class ElasticIpController {
  @Autowired
  ElasticIpService elasticIpService

  @RequestMapping(method = RequestMethod.GET)
  List<Map> getAllForAccount(@PathVariable("account") String account) {
    return elasticIpService.getForAccount(account)
  }

  @RequestMapping(value="/{region}", method = RequestMethod.GET)
  List<Map> getAllForAccountAndRegion(@PathVariable("account") String account,
                                      @PathVariable("region") String region) {
    return elasticIpService.getForAccountAndRegion(account, region)
  }
}
