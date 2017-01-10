/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.elasticsearch.events

import com.netflix.spinnaker.clouddriver.elasticsearch.ElasticSearchServerGroupTagger
import com.netflix.spinnaker.clouddriver.orchestration.events.DeleteServerGroupEvent
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEvent
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll;

class DeleteServerGroupEventHandlerSpec extends Specification {
  def serverGroupTagger = Mock(ElasticSearchServerGroupTagger)

  @Subject
  def eventHandler = new DeleteServerGroupEventHandler(serverGroupTagger)

  @Unroll
  void "should only handle DeleteServerGroupEvent"() {
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
    eventHandler.handle(new DeleteServerGroupEvent("aws", "accountId", "region", "serverGroup-v001"))

    then:
    1 * serverGroupTagger.deleteAll("aws", "accountId", "region", "serverGroup-v001")
  }



}
