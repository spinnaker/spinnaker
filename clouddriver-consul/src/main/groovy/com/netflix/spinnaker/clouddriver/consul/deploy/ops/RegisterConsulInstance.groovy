/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.consul.deploy.ops

import com.netflix.spinnaker.clouddriver.consul.api.v1.ConsulAgent
import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import com.netflix.spinnaker.clouddriver.consul.config.ConsulProperties

// The difference between "Register" and "EnableDisable" is that "Register" first joins a node to the Consul cluster,
// whereas "EnableDisable" keeps a node in a cluster, but changes its discovery status
class RegisterConsulInstance {
  static void operate(ConsulConfig config, String agentEndpoint) {
    def agent = new ConsulAgent("${agentEndpoint}:${config.agentPort}")
    agent.api.join(config.servers[0], 0 /* Not joining the WAN, since this is a client node */ )
    return
  }
}
