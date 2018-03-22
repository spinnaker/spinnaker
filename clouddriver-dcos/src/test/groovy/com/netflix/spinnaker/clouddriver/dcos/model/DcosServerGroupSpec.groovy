/*
 * Copyright 2018 Cerner Corporation
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

package com.netflix.spinnaker.clouddriver.dcos.model

import mesosphere.marathon.client.model.v2.App
import spock.lang.Specification
import spock.lang.Subject


class DcosServerGroupSpec extends Specification {
  private static final String ACCOUNT = "testaccount"
  static final private String APP = "testapp"
  static final private String SERVER_GROUP_NAME = "${APP}-v000"
  static final private String REGION = "region"
  static final private String GROUP = "group"
  static final private String MARATHON_APP = "/${ACCOUNT}/${GROUP}/${SERVER_GROUP_NAME}"
  static final private String DCOS_URL = "https://test.com/"

  def setup() {
  }

  void "load balancers associated to the server group that don't have the same account won't be populated"() {
    setup:
    def lbApp = createApp(MARATHON_APP)
    def goodLb = "${APP}-goodlb"
    def badLb = "${APP}-badlb"
    lbApp.getLabels() >> ["HAPROXY_GROUP": "${ACCOUNT}_${goodLb},wrongaccount_${badLb}"]

    when:
    @Subject def serverGroup = new DcosServerGroup(REGION, DCOS_URL, lbApp)

    then:
    serverGroup.loadBalancers.size() == 1
    serverGroup.loadBalancers.first() == goodLb.toString()
  }

  // TODO build info

  def createApp(id) {
    Stub(App) {
      getId() >> id
    }
  }
}
