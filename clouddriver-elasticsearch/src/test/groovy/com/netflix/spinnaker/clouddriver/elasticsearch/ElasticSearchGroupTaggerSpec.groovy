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

package com.netflix.spinnaker.clouddriver.elasticsearch

import spock.lang.Specification;

class ElasticSearchGroupTaggerSpec extends Specification {
  void "should construct valid UpsertEntityTagsDescription"() {
    when:
    def description = ElasticSearchServerGroupTagger.upsertEntityTagsDescription(
      "myCloudProvider", "100", "us-east-1", "myServerGroup-v001", "MY_EVENT", "This server group failed to launch!"
    )

    then:
    description.isPartial
    description.entityRef.region == "us-east-1"
    description.entityRef.accountId == "100"
    description.entityRef.entityType == "servergroup"
    description.entityRef.entityId == "myServerGroup-v001"
    description.entityRef.cloudProvider == "myCloudProvider"

    description.tags.size() == 1
    description.tags[0].name == "spinnaker_ui_alert:my_event"
    description.tags[0].value == [
      message: "This server group failed to launch!",
      type   : "alert"
    ]
  }
}
