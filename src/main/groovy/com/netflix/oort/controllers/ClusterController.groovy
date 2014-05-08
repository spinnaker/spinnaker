package com.netflix.oort.controllers

import com.netflix.frigga.ami.AppVersion
import com.netflix.oort.clusters.Cluster
import com.netflix.oort.clusters.ClusterProvider
import com.netflix.oort.clusters.ServerGroup
import com.netflix.oort.deployables.Deployable
import com.netflix.oort.deployables.DeployableProvider
import com.netflix.oort.remoting.AggregateRemoteResource
import javax.servlet.http.HttpServletResponse
import org.apache.commons.codec.binary.Base64
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/deployables/{deployable}/clusters")
class ClusterController {

  @Autowired
  AggregateRemoteResource edda

  @Autowired
  List<DeployableProvider> deployableProviders

  @Autowired
  List<ClusterProvider> clusterProviders

  @RequestMapping(method = RequestMethod.GET)
  def list(@PathVariable("deployable") String deployable) {
    Map<String, Deployable> deployables = [:]
    deployableProviders.each {
      def deployableObject = it.get(deployable)
      if (deployables.containsKey(deployableObject.name)) {
        def existing = deployables[deployableObject.name]
        deployables[deployableObject.name] = Deployable.merge(existing, deployableObject)
      } else {
        deployables[deployableObject.name] = deployableObject
      }
    }
    deployables.values()?.getAt(0)?.clusters?.list()
  }

  @RequestMapping(value = "/{cluster}", method = RequestMethod.GET)
  def get(@PathVariable("deployable") String deployable, @PathVariable("cluster") String clusterName,
          @RequestParam(value = "zone", required = false) String zoneName) {
    clusterProviders.collect {
      zoneName ? [it.getByNameAndZone(deployable, clusterName, zoneName)] : it.getByName(deployable, clusterName)
    }
  }

  @RequestMapping(value = "/{cluster}/serverGroups/{serverGroup}/{region}", method = RequestMethod.GET)
  def getAsg(@PathVariable("deployable") String deployable, @PathVariable("cluster") String clusterName,
             @PathVariable("serverGroup") String serverGroupName, @PathVariable("region") String region, HttpServletResponse response) {
    def map = DeployableController.Cacher.get().get(deployable)
    if (map.clusters.containsKey(clusterName)) {
      def asgs = map.clusters."$clusterName"."$region"
      def found = asgs.find { it.autoScalingGroupName == asgName }
      if (found) {
        def converted = convertAsgMap(clusterName, region, found)
        converted.asg = found
        converted.instances = converted.instances.collect { getInstance(region, it.instanceId) }
        return converted
      }
    } else {
      response.sendError 404
    }
  }

  def toAsgList(Map map) {
    def list = []
    map.clusters.each { String cluster, Map obj ->
      obj.each { String region, List<Map> asgs ->
        asgs.each { Map asg ->
          list << convertAsgMap(cluster, region, asg)
        }
      }
    }
    list
  }

  def convertAsgMap(String cluster, String region, Map asg) {
    def extra = getExtraDetails(asg.launchConfigurationName, region)
    def launchConfig = extra.launchConfig
    def userData = extra.userData
    def image = extra.image

    def resp = [name: asg.autoScalingGroupName, region: region, cluster: cluster, instances: asg.instances,
                created: asg.createdTime, launchConfig: launchConfig, userData: userData]

    def buildVersion = image.tags.find { it.key == "appversion" }?.value
    if (buildVersion) {
      def props = AppVersion.parseName(buildVersion)?.properties
      if (props) {
        resp.buildInfo = ["buildNumber", "commit", "packageName", "buildJobName"].collectEntries {
          [(it): props[it]]
        }
      }
    }

    resp
  }

  def getExtraDetails(String launchConfigName, String region) {
    def launchConfig = edda.getRemoteResource(region).get("/REST/v2/aws/launchConfigurations/$launchConfigName")
    def image = edda.getRemoteResource(region).get("/REST/v2/aws/images/$launchConfig.imageId")
    [launchConfig: launchConfig, userData: new String(Base64.decodeBase64(launchConfig.userData as String)), image: image]
  }

  def getInstance(String region, String instanceId) {
    try {
      edda.getRemoteResource(region) get "/REST/v2/view/instances/$instanceId"
    } catch (IGNORE) { [instanceId: instanceId, state: [name: "offline"]] }
  }
}
