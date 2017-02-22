package com.netflix.spinnaker.clouddriver.elasticsearch.events

import com.netflix.spinnaker.clouddriver.elasticsearch.ElasticSearchServerGroupTagger
import com.netflix.spinnaker.clouddriver.orchestration.events.CreateServerGroupEvent
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEvent
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class CreateServerGroupEventHandlerSpec extends Specification {
  def serverGroupTagger = Mock(ElasticSearchServerGroupTagger)

  @Subject
  def eventHandler = new CreateServerGroupEventHandler(serverGroupTagger)

  @Unroll
  void "should only handle events of type CreateServerGroupEvent"() {
    given:
    def operationEvent = Mock(OperationEvent) {
      getType() >> { return OperationEvent.Type.SERVER_GROUP }
      getAction() >> { return OperationEvent.Action.CREATE }
      getCloudProvider() >> { return "aws" }
    }

    when:
    eventHandler.handle(operationEvent)

    then:
    0 * serverGroupTagger.deleteAll(_, _, _, _)

    when:
    eventHandler.handle(new CreateServerGroupEvent("aws", "accountId", "region", "serverGroup-v001"))

    then:
    1 * serverGroupTagger.deleteAll("aws", "accountId", "region", "serverGroup-v001")
  }
}
