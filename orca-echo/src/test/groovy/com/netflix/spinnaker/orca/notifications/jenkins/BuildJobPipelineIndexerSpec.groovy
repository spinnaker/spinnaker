package com.netflix.spinnaker.orca.notifications.jenkins

import com.netflix.spinnaker.orca.mayo.services.PipelineConfigurationService
import retrofit.MockHttpException
import rx.schedulers.Schedulers
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static java.util.concurrent.TimeUnit.SECONDS

class BuildJobPipelineIndexerSpec extends Specification {

  def pipelineConfigurationService = Mock(PipelineConfigurationService)

  @Subject
  def pipelineIndexer = new BuildJobPipelineIndexer(pipelineConfigurationService)
  @Shared def scheduler = Schedulers.test()

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

  def setup() {
    pipelineIndexer.scheduler = scheduler
  }

  def "should add multiple pipeline targets to single trigger type"() {
    given:
    pipelineConfigurationService.getPipelines() >> [pipeline1, pipeline2, pipeline3]

    and:
    pipelineIndexer.init()

    when:
    scheduler.advanceTimeBy(pipelineIndexer.pollingInterval, SECONDS)

    then:
    pipelineIndexer.pipelines[key].name == ["pipeline1", "pipeline2"]

    cleanup:
    pipelineIndexer.shutdown()

    where:
    key = new Trigger("master1", "SPINNAKER-package-pond")
  }

  def "should continue polling despite errors"() {
    given: "we'll get an error the second time we poll"
    pipelineConfigurationService.getPipelines() >> [] >> {
      throw MockHttpException.newInternalError(null)
    }

    and: "we have polled twice"
    pipelineIndexer.init()
    scheduler.advanceTimeBy(pipelineIndexer.pollingInterval * 2, SECONDS)

    when: "the next poll interval occurs"
    scheduler.advanceTimeBy(pipelineIndexer.pollingInterval, SECONDS)

    then: "the polling thread hasn't died"
    1 * pipelineConfigurationService.getPipelines() >> []

    cleanup:
    pipelineIndexer.shutdown()

    where:
    key = new Trigger("master1", "SPINNAKER-package-pond")
  }
}
