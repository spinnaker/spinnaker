/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.LoadBalancerService
import groovy.transform.CompileStatic
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@CompileStatic
@RestController
class LoadBalancerController {

  @Autowired
  LoadBalancerService loadBalancerService

  @ApiOperation(value = "Retrieve a list of load balancers for a given cloud provider")
  @RequestMapping(value = '/loadBalancers', method = RequestMethod.GET)
  List getAll(@RequestParam(value = "provider", defaultValue = "aws", required = false) String provider) {
    loadBalancerService.getAll(provider)
  }

  @ApiOperation(value = "Retrieve a load balancer for a given cloud provider")
  @RequestMapping(value = "/loadBalancers/{name:.+}", method = RequestMethod.GET)
  Map getLoadBalancer(@PathVariable String name,
                      @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider) {
    loadBalancerService.get(name, provider)
  }

  @ApiOperation(value = "Retrieve a load balancer's details as a single element list for a given account, region, cloud provider and load balancer name")
  @RequestMapping(value = "/loadBalancers/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  List<Map> getLoadBalancerDetails(@PathVariable String account,
                                   @PathVariable String region,
                                   @PathVariable String name,
                                   @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider) {
    loadBalancerService.getDetailsForAccountAndRegion(account, region, name, provider)
  }

  @ApiOperation(value = "Retrieve a list of load balancers for a given application")
  @RequestMapping(value = '/applications/{application}/loadBalancers', method = RequestMethod.GET)
  List<Map> getApplicationLoadBalancers(@PathVariable String application) {
    loadBalancerService.getApplicationLoadBalancers(application)
  }
}
