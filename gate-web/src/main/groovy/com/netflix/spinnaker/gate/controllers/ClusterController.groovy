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

import com.netflix.spinnaker.gate.services.ClusterService
import com.netflix.spinnaker.gate.services.LoadBalancerService
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@CompileStatic
@RequestMapping("/applications/{application}/clusters")
@RestController
class ClusterController {

  @Autowired
  ClusterService clusterService

  @Autowired
  LoadBalancerService loadBalancerService

  @Operation(summary = "Retrieve a list of cluster names for an application, grouped by account")
  @RequestMapping(method = RequestMethod.GET)
  Map getClusters(@PathVariable("application") String app,
                  @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    clusterService.getClusters(app, sourceApp)
  }

  @Operation(summary = "Retrieve a list of clusters for an account")
  @RequestMapping(value = "/{account}", method = RequestMethod.GET)
  List<Map> getClusters(@PathVariable("application") String app,
                        @PathVariable("account") String account,
                        @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    clusterService.getClustersForAccount(app, account, sourceApp)
  }

  @Operation(summary = "Retrieve a cluster's details")
  @RequestMapping(value = "/{account}/{clusterName:.+}", method = RequestMethod.GET)
  Map getClusters(@PathVariable("application") String app,
                  @PathVariable("account") String account,
                  @PathVariable("clusterName") String clusterName,
                  @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    clusterService.getCluster(app, account, clusterName, sourceApp)
  }

  @Operation(summary = "Retrieve a cluster's loadbalancers")
  @RequestMapping(value = "/{account}/{clusterName}/{type}/loadBalancers", method = RequestMethod.GET)
  List getClusterLoadBalancers(@PathVariable String applicationName,
                               @PathVariable String account,
                               @PathVariable String clusterName,
                               @PathVariable String type,
                               @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    loadBalancerService.getClusterLoadBalancers(applicationName, account, type, clusterName, sourceApp)
  }

  @Operation(summary = "Retrieve a list of server groups for a cluster")
  @RequestMapping(value = "/{account}/{clusterName}/serverGroups", method = RequestMethod.GET)
  List<Map> getServerGroups(@PathVariable("application") String app,
                            @PathVariable("account") String account,
                            @PathVariable("clusterName") String clusterName,
                            @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    clusterService.getClusterServerGroups(app, account, clusterName, sourceApp)
  }

  @Operation(summary = "Retrieve a list of scaling activities for a server group")
  @RequestMapping(value = "/{account}/{clusterName}/serverGroups/{serverGroupName}/scalingActivities", method = RequestMethod.GET)
  List<Map> getScalingActivities(@PathVariable("application") String app,
                                 @PathVariable("account") String account,
                                 @PathVariable("clusterName") String clusterName,
                                 @PathVariable("serverGroupName") String serverGroupName,
                                 @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider,
                                 @RequestParam(value = "region", required = false) String region,
                                 @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    clusterService.getScalingActivities(app, account, clusterName, serverGroupName, provider, region, sourceApp)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  @Operation(summary = "Retrieve a server group's details")
  @RequestMapping(value = "/{account}/{clusterName}/serverGroups/{serverGroupName:.+}", method = RequestMethod.GET)
  List<Map> getServerGroups(@PathVariable("application") String app,
                            @PathVariable("account") String account,
                            @PathVariable("clusterName") String clusterName,
                            @PathVariable("serverGroupName") String serverGroupName,
                            @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    // TODO this crappy logic needs to be here until the "type" field is removed in Clouddriver
    clusterService.getClusterServerGroups(app, account, clusterName, sourceApp).findAll {
      it.name == serverGroupName
    }
  }

  @Operation(summary = "Retrieve a server group that matches a target coordinate (e.g., newest, ancestor) relative to a cluster",
                description = "`scope` is either a zone or a region")
  @RequestMapping(value = "/{account:.+}/{clusterName:.+}/{cloudProvider}/{scope}/serverGroups/target/{target:.+}", method = RequestMethod.GET)
  Map getTargetServerGroup(@PathVariable("application") String app,
                           @PathVariable("account") String account,
                           @PathVariable("clusterName") String clusterName,
                           @PathVariable("cloudProvider") String cloudProvider,
                           @PathVariable("scope") String scope,
                           @PathVariable("target") String target,
                           @RequestParam(value = "onlyEnabled", required = false) Boolean onlyEnabled,
                           @RequestParam(value = "validateOldest", required = false) Boolean validateOldest,
                           @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    clusterService.getTargetServerGroup(app, account, clusterName, cloudProvider, scope, target, onlyEnabled, validateOldest, sourceApp)
  }
}
