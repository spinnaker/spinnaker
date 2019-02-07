package com.netflix.spinnaker.orca.pipeline.util

import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class RegionCollectorSpec extends Specification {
  @Subject
  def regionCollector = new RegionCollector()

  def "should find all regions from subsequent deploys"() {
    given:
    def execution = pipeline {
      stage {
        name = "findImage"
        type = "findImage"
        refId = "1"
        context = [ cloudProvider: "aws"]
      }

      stage {
        name = "deploy"
        type = "deploy"
        context = [
          clusters: deployClusters
        ]
        requisiteStageRefIds = ["1"]
      }
    }

    when:
    def regionSet = regionCollector.getRegionsFromChildStages(execution.stageByRef("1"))

    then:
    regionSet == ["region1", "region2", "region3"] as Set

    where:
    deployClusters = [
      makeCluster(["region1", "region2"]),
      makeCluster(["region3"])
    ]
  }

  def "should find all regions from subsequent canary deploys"() {
    given:
    def execution = pipeline {
      stage {
        name = "findImage"
        type = "findImage"
        refId = "1"
        context = [cloudProvider: "aws"]
      }

      stage {
        name = "canary"
        type = "canary"
        context = [
          clusterPairs: [[
                           baseline: deployCluster1,
                           canary  : deployCluster2
                         ],
                         [
                           baseline: deployCluster3
                         ]
          ]
        ]
        requisiteStageRefIds = ["1"]
      }
    }

    when:
    def regionSet = regionCollector.getRegionsFromChildStages(execution.stageByRef("1"))

    then:
    regionSet == ["region1", "region2", "region3", "region4"] as Set

    where:
    deployCluster1 = makeCluster(["region1", "region2"])
    deployCluster2 = makeCluster(["region3"])
    deployCluster3 = makeCluster(["region4"])
  }

  private makeCluster(List regions) {
    def aZones = new HashMap()
    regions.each { it -> aZones.put(it, []) }

    return [
            cloudProvider: "aws",
            availabilityZones: aZones
    ]
  }
}


