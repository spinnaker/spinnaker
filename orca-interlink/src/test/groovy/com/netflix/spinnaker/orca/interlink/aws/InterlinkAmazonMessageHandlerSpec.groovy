/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.interlink.aws

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.interlink.events.DeleteInterlinkEvent
import com.netflix.spinnaker.orca.pipeline.CompoundExecutionOperator
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Specification

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE


class InterlinkAmazonMessageHandlerSpec extends Specification {
  def 'checks if the execution repo handles the partition of the event'() {
    given:
    def executionRepo = Mock(ExecutionRepository)
    def executionOperator = Mock(CompoundExecutionOperator)
    def handler = new InterlinkAmazonMessageHandler(
        Mock(ObjectMapper),
        executionRepo,
        executionOperator
    )

    when:
    handler.handleInternal(new DeleteInterlinkEvent(PIPELINE, "id").withPartition("local"))

    then:
    1 * executionRepo.handlesPartition("local") >> true
    1 * executionOperator.delete(PIPELINE, "id")

    when:
    handler.handleInternal(new DeleteInterlinkEvent(PIPELINE, "id").withPartition("foreign"))

    then:
    1 * executionRepo.handlesPartition("foreign") >> false
    0 * executionOperator.delete(PIPELINE, "id")
  }
}
