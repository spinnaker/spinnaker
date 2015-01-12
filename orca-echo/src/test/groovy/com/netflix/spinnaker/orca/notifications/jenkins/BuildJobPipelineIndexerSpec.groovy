package com.netflix.spinnaker.orca.notifications.jenkins

import com.netflix.spinnaker.orca.mayo.services.PipelineConfigurationService
import spock.lang.Specification
import spock.lang.Subject

class BuildJobPipelineIndexerSpec extends Specification {

  def pipelineConfigurationService = Stub(PipelineConfigurationService)

  @Subject
  def pipelineIndexer = new BuildJobPipelineIndexer(pipelineConfigurationService)

  def pipeline1 = [
      name    : "pipeline1",
      triggers: [[type  : "jenkins",
                  job   : "SPINNAKER-package-pond",
                  master: "master1"]],
      stages  : [[type: "bake"],
                 [type: "deploy", cluster: [name: "bar"]]]
  ]

  def pipeline2 = [
      name    : "pipeline2",
      triggers: [[type  : "jenkins",
                  job   : "SPINNAKER-package-pond",
                  master: "master1"]],
      stages  : [[type: "bake"],
                 [type: "deploy", cluster: [name: "foo"]]]
  ]

  def pipeline3 = [
      name    : "pipeline3",
      triggers: [[type  : "jenkins",
                  job   : "SPINNAKER-package-pond",
                  master: "master2"]],
      stages  : []
  ]

  void "should add multiple pipeline targets to single trigger type"() {
    setup:
    pipelineConfigurationService.getPipelines() >> [pipeline1, pipeline2, pipeline3]

    when:
    pipelineIndexer.run()

    then:
    pipelineIndexer.pipelines[key].name == ["pipeline1", "pipeline2"]

    where:
    key = new Trigger("master1", "SPINNAKER-package-pond")
  }
}
