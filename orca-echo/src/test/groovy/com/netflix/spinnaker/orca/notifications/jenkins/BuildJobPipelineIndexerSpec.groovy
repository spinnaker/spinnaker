package com.netflix.spinnaker.orca.notifications.jenkins

import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.mayo.MayoService
import retrofit.MockHttpException
import retrofit.client.Response
import retrofit.mime.TypedString
import rx.schedulers.Schedulers
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static java.util.concurrent.TimeUnit.SECONDS

class BuildJobPipelineIndexerSpec extends Specification {

  def mayoService = Mock(MayoService)
  def mapper = new OrcaObjectMapper()

  @Subject
  def pipelineIndexer = new BuildJobPipelineIndexer(mayoService, mapper)

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
    mayoService.getPipelines() >> mayoResponse(pipeline1, pipeline2, pipeline3)

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
    mayoService.getPipelines() >> mayoResponse() >> {
      throw MockHttpException.newInternalError(null)
    }

    and: "we have polled twice"
    pipelineIndexer.init()
    scheduler.advanceTimeBy(pipelineIndexer.pollingInterval * 2, SECONDS)

    when: "the next poll interval occurs"
    scheduler.advanceTimeBy(pipelineIndexer.pollingInterval, SECONDS)

    then: "the polling thread hasn't died"
    1 * mayoService.getPipelines() >> mayoResponse()

    cleanup:
    pipelineIndexer.shutdown()

    where:
    key = new Trigger("master1", "SPINNAKER-package-pond")
  }

  private Response mayoResponse(Map... pipelines) {
    new Response(
        "http://mayo", 200, "OK", [],
        new TypedString(mapper.writeValueAsString(pipelines))
    )
  }

}
