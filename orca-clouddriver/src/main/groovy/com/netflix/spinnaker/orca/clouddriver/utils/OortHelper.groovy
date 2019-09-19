/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.converter.ConversionException
import retrofit.converter.JacksonConverter

/**
 * Helper methods for filtering Cluster/ASG/Instance information from Oort
 */
@Component
class OortHelper {
  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  List<Map> getSearchResults(String searchTerm, String type, String platform) {
    convert(oortService.getSearchResults(searchTerm, type, platform), List)
  }

  private <T> T convert(Response response, Class<T> type) {
    def converter = new JacksonConverter(objectMapper)
    try {
      return type.cast(converter.fromBody(response.body, type))
    } catch (ConversionException ce) {
      throw RetrofitError.conversionError(response.url, response, converter, type, ce)
    }
  }

  Optional<Map> getCluster(String application, String account, String cluster, String cloudProvider) {
    return convertedResponse(Map) { oortService.getCluster(application, account, cluster, cloudProvider) }
  }

  Optional<TargetServerGroup> getTargetServerGroup(String account,
                                                   String serverGroupName,
                                                   String location,
                                                   String cloudProvider = null) {
    return convertedResponse(Map) {
      oortService.getServerGroup(account, location, serverGroupName)
    }.map({ Map serverGroup -> new TargetServerGroup(serverGroup) })
  }

  public <T> Optional<T> convertedResponse(Class<T> type, Closure<Response> request) {
    Response r
    try {
      r = request.call()
    } catch (RetrofitError re) {
      if (re.kind == RetrofitError.Kind.HTTP && re.response.status == 404) {
        return Optional.empty()
      }
      throw re
    }
    return Optional.of(convert(r, type))
  }

  Map getInstancesForCluster(Map context, String expectedAsgName = null, boolean expectOneAsg = false, boolean failIfAnyInstancesUnhealthy = false) {
    // infer the app from the cluster prefix since this is used by quip and we want to be able to quick patch different apps from the same pipeline
    def app
    def clusterName
    if(expectedAsgName) {
      app = expectedAsgName.substring(0, expectedAsgName.indexOf("-"))
      clusterName = expectedAsgName.substring(0, expectedAsgName.lastIndexOf("-"))
    } else if(context?.clusterName?.indexOf("-") > 0) {
      app = context.clusterName.substring(0, context.clusterName.indexOf("-"))
      clusterName = context.clusterName
    } else {
      app = context.clusterName
      clusterName = context.clusterName
    }

    def response = oortService.getCluster(app, context.account, clusterName, context.cloudProvider ?: context.providerType ?: "aws")
    def oortCluster = convert(response, Map)
    def instanceMap = [:]

    if (!oortCluster || !oortCluster.serverGroups) {
      throw new RuntimeException("unable to find any server groups")
    }

    def region = context.region ?: context.source.region

    if(!region) {
      throw new RuntimeException("unable to determine region")
    }

    def asgsForCluster = oortCluster.serverGroups.findAll {
      it.region == region
    }

    def searchAsg
    if (expectOneAsg) {
      if(asgsForCluster.size() != 1) {
        throw new RuntimeException("there is more than one server group in the cluster : ${clusterName}:${region}")
      }
      searchAsg = asgsForCluster.get(0)
    } else if(expectedAsgName) {
      searchAsg = asgsForCluster.findResult {
        if(it.name == expectedAsgName) {
        return it
        }
      }
      if(!searchAsg) {
        throw new RuntimeException("did not find the expected asg name : ${expectedAsgName}")
      }
    }

    searchAsg.instances.each { instance ->
      String hostName = instance.publicDnsName
      if(!hostName || hostName.isEmpty()) { // some instances dont have a public address, fall back to the private ip
        hostName = instance.privateIpAddress
      }

      String healthCheckUrl
      instance.health.eachWithIndex { health, idx ->
        if (health.healthCheckUrl != null && !health.healthCheckUrl.isEmpty()) {
          healthCheckUrl = health.healthCheckUrl
        }
      }

      def status = instance.health.find { healthItem ->
        healthItem.find {
          key, value ->
            key == "status"
        }
      }?.status

      if(failIfAnyInstancesUnhealthy && (!healthCheckUrl || !status || status != "UP")) {
        throw new RuntimeException("at least one instance is DOWN or in the STARTING state, exiting")
      }

      Map instanceInfo = [
        hostName : hostName,
        healthCheckUrl : healthCheckUrl,
        privateIpAddress: instance.privateIpAddress
      ]
      instanceMap.put(instance.instanceId, instanceInfo)
    }

    return instanceMap
  }
}
