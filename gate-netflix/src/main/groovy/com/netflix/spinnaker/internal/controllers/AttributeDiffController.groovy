package com.netflix.spinnaker.internal.controllers

import com.netflix.spinnaker.internal.services.AttributeDiffService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RestController
@RequestMapping("/diff")
class AttributeDiffController {

  @Autowired
  AttributeDiffService attributeDiffService

  @RequestMapping(value = "/cluster/{account}/{clusterName}", method = RequestMethod.GET)
  Map getServerGroupDiff(@PathVariable("account") String account,
                      @PathVariable("clusterName") String clusterName) {
    attributeDiffService.getServerGroupDiff(account, clusterName)
  }

}
