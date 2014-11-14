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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.async.DeferredResult


import static com.netflix.spinnaker.gate.controllers.AsyncControllerSupport.defer

@CompileStatic
@RestController
class LoadBalancerController {

  @Autowired
  LoadBalancerService loadBalancerService

  @RequestMapping(value = "/applications/{applicationName}/clusters/{account}/{clusterName}/{type}/loadBalancers", method = RequestMethod.GET)
  DeferredResult<List> getAllForCluster(
      @PathVariable String applicationName,
      @PathVariable String account, @PathVariable String clusterName, @PathVariable String type) {
    defer loadBalancerService.getClusterLoadBalancers(applicationName, account, type, clusterName)
  }

  @RequestMapping(value = "/loadBalancers", method = RequestMethod.GET)
  DeferredResult<HttpEntity> getAll(
      @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
      @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
    def obs = loadBalancerService.getAll(offset, size).map({
      def headers = new HttpHeaders()
      headers.add("X-Result-Total", Integer.valueOf(it.totalMatches as String).toString())
      headers.add("X-Result-Offset", offset.toString())
      headers.add("X-Result-Size", Integer.valueOf(it.pageSize as String).toString())
      new HttpEntity(it.results, headers)
    })
    defer obs
  }


}
