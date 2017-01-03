package com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class WaitForAppEngineServerGroupStartTaskSpec extends Specification {
  @Shared OortService oort
  @Shared ObjectMapper mapper = new OrcaObjectMapper()
  @Subject WaitForAppEngineServerGroupStartTask task = new WaitForAppEngineServerGroupStartTask() {
    {
      objectMapper = mapper
    }
  }

  void "should properly wait for the server group to start"() {
    setup:
      oort = Mock(OortService)
      oort.getCluster("app",
                      "my-appengine-account",
                      "app-stack-detail",
                      "appengine") >> { new Response("kato", 200, "ok", [], new TypedString(mapper.writeValueAsString(cluster))) }

      task.oortService = oort

      def context = [
        account: "my-appengine-account",
        serverGroupName: "app-stack-detail-v000",
        cloudProvider: "appengine"
      ]

      def stage = new OrchestrationStage(new Orchestration(), "waitForServerGroupStart", context)

    when:
      def result = task.execute(stage)

    then:
      result.status == ExecutionStatus.RUNNING

    when:
      cluster.serverGroups[0].servingStatus = "SERVING"

    and:
      result = task.execute(stage)

    then:
      result.status == ExecutionStatus.SUCCEEDED

    where:
      cluster = [
        name: "app-stack-detail",
        account: "my-appengine-account",
        serverGroups: [
          [
            name: "app-stack-detail-v000",
            region: "us-central",
            servingStatus: "STOPPED",
          ]
        ]
      ]
  }
}
