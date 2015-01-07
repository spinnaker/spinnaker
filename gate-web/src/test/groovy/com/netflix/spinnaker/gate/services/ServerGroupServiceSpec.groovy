/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.config.InsightConfiguration
import com.netflix.spinnaker.gate.services.internal.OortService
import spock.lang.Specification

class ServerGroupServiceSpec extends Specification {
  void "should include relevant insight actions for server group"() {
    given:
    def service = new ServerGroupService(
        oortService: Mock(OortService) {
          1 * getServerGroupDetails(_, _, _, _) >> { return [:] }
        },
        insightConfiguration: new InsightConfiguration(
            serverGroup: [new InsightConfiguration.Link(url: "{application}-{account}-{region}-{serverGroup}-{DNE}")]
        )
    )

    expect:
    service.getForApplicationAndAccountAndRegion("application", "account", "region", "serverGroup").insightActions*.url == [
        "application-account-region-serverGroup-{DNE}"
    ]
  }

  void "should include application, account, region and serverGroup in context"() {
    expect:
    ServerGroupService.getContext("app", "prod", "us-west-1", "app-main-v001") == [
        "application": "app", "account": "prod", "region": "us-west-1", "serverGroup": "app-main-v001"
    ]
  }
}
