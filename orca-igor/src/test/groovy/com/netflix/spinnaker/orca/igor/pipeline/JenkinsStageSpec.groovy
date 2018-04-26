package com.netflix.spinnaker.orca.igor.pipeline

import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask
import spock.lang.Specification

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class JenkinsStageSpec extends Specification {

  def "should include BindProducedArtifacts when expecting artifacts"() {
    given:

    def jenkinsStage = new JenkinsStage()

    def stage = stage {
      type = "jenkins"
      context = [
        master           : "builds",
        job              : "orca",
        buildNumber      : 4,
        propertyFile     : "sample.properties",
        expectedArtifacts: [
          [
            matchArtifact: [
              type: "docker/image"
            ]
          ],
        ]
      ]
    }

    when:
    def tasks = jenkinsStage.buildTaskGraph(stage)

    then:
    tasks.iterator().size() == 4
    tasks.findAll {
      it.implementingClass == BindProducedArtifactsTask
    }.size() == 1
  }

  def "should not include BindProducedArtifacts when not expecting artifacts"() {
    given:
    def jenkinsStage = new JenkinsStage()

    def stage = stage {
      type = "jenkins"
      context = [
        master      : "builds",
        job         : "orca",
        buildNumber : 4,
        propertyFile: "sample.properties"
      ]
    }

    when:
    def tasks = jenkinsStage.buildTaskGraph(stage)

    then:
    tasks.iterator().size() == 3
    tasks.findAll {
      it.implementingClass == BindProducedArtifactsTask
    }.size() == 0
  }
}
