package com.netflix.oort.controllers

import com.netflix.frigga.ami.AppVersion
import javax.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestTemplate

@RestController
@RequestMapping("/deployables/{deployable}/clusters")
class ClusterController {

  RestTemplate restTemplate = new RestTemplate()

  @RequestMapping(method = RequestMethod.GET)
  def list(@PathVariable("deployable") String deployable) {
    def map = DeployableController.Cacher.get().get(deployable)
    toAsgList map
  }

  @RequestMapping(value = "/{cluster}", method = RequestMethod.GET)
  def get(@PathVariable("deployable") String deployable, @PathVariable("cluster") String cluster, HttpServletResponse response) {
    def map = DeployableController.Cacher.get().get(deployable)
    if (map.clusters.containsKey(cluster)) {
      toAsgList([clusters: [(cluster): map.clusters."$cluster"]])
    } else {
      response.sendError 404
    }
  }

  @RequestMapping(value = "/{cluster}/asgs/{asg}", method = RequestMethod.GET)
  def getAsg(@PathVariable("deployable") String deployable, @PathVariable("cluster") String clusterName,
             @PathVariable("asg") String asgName, HttpServletResponse response) {
    def map = DeployableController.Cacher.get().get(deployable)
    if (map.clusters.containsKey(clusterName)) {
      def cluster = map.clusters."$clusterName"
      def asgDetails = cluster.collect { String region, List<Map> asgs ->
        def found = asgs.find { it.autoScalingGroupName == asgName }
        if (found) {
          def converted = convertAsgMap(clusterName, region, found)
          converted.asg = found
          converted.instances = converted.instances.collect { getInstance(region, it.instanceId) }
          converted
        } else { null }
      }
      asgDetails.removeAll([null])
      asgDetails?.getAt(0)
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
    def image = extra.image

    def resp = [name: asg.autoScalingGroupName, region: region, cluster: cluster, instances: asg.instances,
                created: asg.createdTime, launchConfig: launchConfig]

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
    def launchConfig = restTemplate.getForEntity("http://entrypoints-v2.${region}.test.netflix.net:7001/REST/v2/aws/launchConfigurations/$launchConfigName", Map).body
    def image = restTemplate.getForEntity("http://entrypoints-v2.${region}.test.netflix.net:7001/REST/v2/aws/images/$launchConfig.imageId", Map).body
    [launchConfig: launchConfig, image: image]
  }

  def getInstance(String region, String instanceId) {
    try {
      restTemplate.getForEntity("http://entrypoints-v2.${region}.test.netflix.net:7001/REST/v2/view/instances/$instanceId", Map).body
    } catch (IGNORE) { [instanceId: instanceId, state: [name: "offline"]] }
  }
}
