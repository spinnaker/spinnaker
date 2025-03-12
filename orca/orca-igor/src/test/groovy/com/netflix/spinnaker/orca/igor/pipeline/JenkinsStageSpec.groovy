package com.netflix.spinnaker.orca.igor.pipeline

import com.netflix.spinnaker.orca.igor.tasks.MonitorJenkinsJobTask
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask
import spock.lang.Specification
import spock.lang.Unroll

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
    tasks.findAll {
      it.implementingClass == BindProducedArtifactsTask
    }.size() == 0
  }

  @Unroll
  def "should wait for completion if set in the stage context"() {
    given:
    def jenkinsStage = new JenkinsStage()

    def stage = stage {
      type = "jenkins"
      context = [
        master: "builds",
        job: "orca",
        buildNumber: 4,
        propertyFile: "sample.properties",
        waitForCompletion: waitForCompletion
      ]
    }

    when:
    def tasks = jenkinsStage.buildTaskGraph(stage)
    def result = tasks.findAll {
      it.implementingClass == MonitorJenkinsJobTask
    }.size() == 1

    then:
    result == didWaitForCompletion

    where:
    waitForCompletion  | didWaitForCompletion
    true               | true
    "true"             | true
    false              | false
    "false"            | false
  }

  def "should wait for completion when waitForCompletion is absent"() {
    given:
    def jenkinsStage = new JenkinsStage()

    def stage = stage {
      type = "jenkins"
      context = [
        master: "builds",
        job: "orca",
        buildNumber: 4,
        propertyFile: "sample.properties"
      ]
    }

    when:
    def tasks = jenkinsStage.buildTaskGraph(stage)

    then:
    tasks.findAll {
      it.implementingClass == MonitorJenkinsJobTask
    }.size() == 1
  }

  def "should not bind artifacts if no expected artifacts were defined"() {
    given:
    def jenkinsStage = new JenkinsStage()

    def stage = stage {
      type = "jenkins"
      context = [
        master: "builds",
        job: "orca",
        buildNumber: 4,
        propertyFile: "sample.properties"
      ]
    }

    when:
    def tasks = jenkinsStage.buildTaskGraph(stage)

    then:
    tasks.findAll {
      it.implementingClass == BindProducedArtifactsTask
    }.size() == 0
  }

  def "should bind artifacts if expected artifacts are defined"() {
    given:
    def jenkinsStage = new JenkinsStage()

    def stage = stage {
      type = "jenkins"
      context = [
        master: "builds",
        job: "orca",
        buildNumber: 4,
        propertyFile: "sample.properties",
        expectedArtifacts: [
          [
            defaultArtifact: [
              customKind: true,
              id: "1d3af620-1a63-4063-882d-ea05eb185b1d",
            ],
            displayName: "my-favorite-artifact",
            id: "547ac2ac-a646-4b8f-8ab4-d7337678b6b6",
            name: "gcr.io/my-registry/my-container",
            useDefaultArtifact: false,
            usePriorArtifact: false,
          ]
        ]
      ]
    }

    when:
    def tasks = jenkinsStage.buildTaskGraph(stage)

    then:
    tasks.findAll {
      it.implementingClass == BindProducedArtifactsTask
    }.size() == 1
  }
}
