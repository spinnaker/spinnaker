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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.async.DeferredResult

@RestController
class LoadBalancerController {

  @Autowired
  LoadBalancerService loadBalancerService

  @RequestMapping(value = "/applications/{applicationName}/clusters/{account}/{clusterName}/{type}/loadBalancers", method = RequestMethod.GET)
  def getAllForCluster(
      @PathVariable String applicationName,
      @PathVariable String account, @PathVariable String clusterName, @PathVariable String type) {
    DeferredResult<List> q = new DeferredResult<>()
    loadBalancerService.getClusterLoadBalancers(applicationName, account, type, clusterName).subscribe({
      q.setResult(it)
    }, { Throwable t ->
      q.setErrorResult(t)
    })
    q
  }

  @RequestMapping(value = "/loadBalancers", method = RequestMethod.GET)
  def getAll(@RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
             @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
    DeferredResult<HttpEntity<List>> q = new DeferredResult<>()
    loadBalancerService.getAll(offset, size).map({
      def headers = new HttpHeaders()
      headers.add("X-Result-Total", Integer.valueOf(it.totalMatches as String).toString())
      headers.add("X-Result-Offset", offset.toString())
      headers.add("X-Result-Size", Integer.valueOf(it.pageSize as String).toString())
      new HttpEntity(it.results, headers)
    }).subscribe({
      q.setResult(it)
    }, { Throwable t ->
      q.setErrorResult(t)
    })
    q
  }


}
