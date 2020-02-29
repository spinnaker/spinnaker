package com.netflix.spinnaker.echo.pipelinetriggers.postprocessors

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.test.RetrofitStubs
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class ExpectedArtifactExpressionEvaluationPostProcessorSpec extends Specification implements RetrofitStubs {
  @Subject
  def artifactPostProcessor = new ExpectedArtifactExpressionEvaluationPostProcessor(EchoObjectMapper.getInstance())

  @Shared
  def trigger = Trigger.builder()
    .enabled(true).type('jenkins')
    .master('master')
    .job('job')
    .buildNumber(100)
    .build()

  def 'evaluates expressions in expected artifacts'() {
    given:
    def artifact = ExpectedArtifact.builder()
      .matchArtifact(
        Artifact.builder()
          .name('''group:artifact:${trigger['buildNumber']}''')
          .version('''${trigger['buildNumber']}''')
          .type('maven/file')
          .build())
      .id('goodId')
      .build()

    def inputPipeline = createPipelineWith([artifact], trigger).withTrigger(trigger)

    when:
    def outputPipeline = artifactPostProcessor.processPipeline(inputPipeline)
    def evaluatedArtifact = outputPipeline.expectedArtifacts[0].matchArtifact

    then:
    evaluatedArtifact.name == 'group:artifact:100'
    evaluatedArtifact.version == '100'
  }

  def 'unevaluable expressions are left in place'() { // they may be evaluated later in a stage with more context
    given:
    def artifact = ExpectedArtifact.builder()
      .matchArtifact(
        Artifact.builder()
          .name('''group:artifact:${#stage('deploy')['version']}''')
          .type('maven/file')
          .build())
        .id('goodId')
        .build()

    def inputPipeline = createPipelineWith([artifact], trigger).withTrigger(trigger)

    when:
    def outputPipeline = artifactPostProcessor.processPipeline(inputPipeline)
    def evaluatedArtifact = outputPipeline.expectedArtifacts[0].matchArtifact

    then:
    evaluatedArtifact.name == '''group:artifact:${#stage('deploy')['version']}'''
  }

  def 'no exception is thrown when expectedArtifacts is null'() {
    def inputPipeline = createPipelineWith(null, trigger).withTrigger(trigger)

    when:
    artifactPostProcessor.processPipeline(inputPipeline)

    then:
    noExceptionThrown()
  }
}
