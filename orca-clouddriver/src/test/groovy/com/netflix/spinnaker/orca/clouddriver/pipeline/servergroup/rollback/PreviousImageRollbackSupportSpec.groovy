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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.clouddriver.FeaturesService
import com.netflix.spinnaker.orca.clouddriver.OortService
import spock.lang.Specification
import spock.lang.Subject;

class PreviousImageRollbackSupportSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def oortService = Mock(OortService)
  def featuresService = Mock(FeaturesService)
  def retrySupport = Spy(RetrySupport) {
    _ * sleep(_) >> { /* do nothing */ }
  }

  @Subject
  def rollbackSupport = new PreviousImageRollbackSupport(objectMapper, oortService, featuresService, retrySupport)

  def "should raise exception if multiple entity tags found"() {
    when:
    rollbackSupport.getImageDetailsFromEntityTags("aws", "test", "us-west-2", "application-v002")

    then:
    1 * featuresService.areEntityTagsAvailable() >> { return true }
    1 * oortService.getEntityTags(*_) >> {
      return [
        [id: "1"],
        [id: "2"]
      ]
    }

    def e = thrown(IllegalStateException)
    e.message == "More than one set of entity tags found for aws:serverGroup:application-v002:test:us-west-2"
  }

  def "should not attempt to fetch entity tags when not enabled"() {
    when:
    def imageDetails = rollbackSupport.getImageDetailsFromEntityTags("aws", "test", "us-west-2", "application-v002")

    then:
    1 * featuresService.areEntityTagsAvailable() >> { return false }
    0 * oortService.getEntityTags(*_)

    imageDetails == null
  }
}
