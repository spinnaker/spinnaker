/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import org.openstack4j.model.network.ext.LoadBalancerV2
import spock.lang.Specification

class BlockingStatusCheckerSpec extends Specification {

  void 'test execute success' () {
    given:
    BlockingStatusChecker adapter = BlockingStatusChecker.from(60, 5) { true }
    LoadBalancerV2 loadBalancer = Mock(LoadBalancerV2)

    when:
    LoadBalancerV2 result = adapter.execute { loadBalancer }

    then:
    result == loadBalancer
  }

  void 'test execute timeout' () {
    given:
    BlockingStatusChecker adapter = BlockingStatusChecker.from (1, 3) {
      false
    }
    LoadBalancerV2 loadBalancer = Mock(LoadBalancerV2)

    when:
    adapter.execute {
      loadBalancer
    }

    then:
    thrown(OpenstackProviderException)
  }
}
