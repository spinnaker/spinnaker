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
import com.netflix.spinnaker.gate.services.ElasticIpService
import com.netflix.spinnaker.gate.services.LoadBalancerService
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
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

  @Autowired
  ElasticIpService elasticIpService

  @RequestMapping(method = RequestMethod.GET)
  Map getClusters(@PathVariable("application") String app) {
    clusterService.getClusters(app)
  }

  @RequestMapping(value = "/{account}", method = RequestMethod.GET)
  List<Map> getClusters(@PathVariable("application") String app, @PathVariable("account") String account) {
    clusterService.getClustersForAccount(app, account)
  }

  @RequestMapping(value = "/{account}/{clusterName:.+}", method = RequestMethod.GET)
  Map getClusters(@PathVariable("application") String app,
                  @PathVariable("account") String account,
                  @PathVariable("clusterName") String clusterName) {
    clusterService.getCluster(app, account, clusterName)
  }

  @RequestMapping(value = "/{account}/{clusterName}/{type}/loadBalancers", method = RequestMethod.GET)
  List getClusterLoadBalancers(
      @PathVariable String applicationName,
      @PathVariable String account, @PathVariable String clusterName, @PathVariable String type) {
    loadBalancerService.getClusterLoadBalancers(applicationName, account, type, clusterName)
  }

  @RequestMapping(value = "/{account}/{clusterName}/serverGroups", method = RequestMethod.GET)
  List<Map> getServerGroups(@PathVariable("application") String app,
                            @PathVariable("account") String account,
                            @PathVariable("clusterName") String clusterName) {
    clusterService.getClusterServerGroups(app, account, clusterName)
  }

  @RequestMapping(value = "/{account}/{clusterName}/serverGroups/{serverGroupName}/scalingActivities", method = RequestMethod.GET)
  List<Map> getScalingActivities(@PathVariable("application") String app,
                                 @PathVariable("account") String account,
                                 @PathVariable("clusterName") String clusterName,
                                 @PathVariable("serverGroupName") String serverGroupName,
                                 @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider,
                                 @RequestParam(value = "region", required = false) String region) {
    clusterService.getScalingActivities(app, account, clusterName, serverGroupName, provider, region)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  @RequestMapping(value = "/{account}/{clusterName}/serverGroups/{serverGroupName:.+}", method = RequestMethod.GET)
  List<Map> getServerGroups(@PathVariable("application") String app,
                            @PathVariable("account") String account,
                            @PathVariable("clusterName") String clusterName,
                            @PathVariable("serverGroupName") String serverGroupName) {
    // TODO this crappy logic needs to be here until the "type" field is removed in Oort
    clusterService.getClusterServerGroups(app, account, clusterName).findAll {
      it.name == serverGroupName
    }
  }

  @RequestMapping(value = "/{account}/{clusterName}/tags", method = RequestMethod.GET)
  List<String> getClusterTags(@PathVariable("clusterName") String clusterName) {
    clusterService.getClusterTags(clusterName)
  }

  @RequestMapping(value = "/{account}/{clusterName}/elasticIps", method = RequestMethod.GET)
  List<Map> getClusterElasticIps(@PathVariable("application") String application,
                                 @PathVariable("account") String account,
                                 @PathVariable("clusterName") String clusterName) {
    elasticIpService.getForCluster(application, account, clusterName)
  }

  @RequestMapping(value = "/{account}/{clusterName}/elasticIps/{region}", method = RequestMethod.GET)
  List<Map> getClusterElasticIpsForRegion(@PathVariable("application") String application,
                                          @PathVariable("account") String account,
                                          @PathVariable("clusterName") String clusterName,
                                          @PathVariable("region") String region) {
    elasticIpService.getForClusterAndRegion(application, account, clusterName, region)
  }
}
