/*
 * Copyright 2016 Netflix, Inc.
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

class EnableDisableConsulInstance {
  static enum State {
    enable,
    disable,
  }

  static void operate(ConsulConfig config, String agentEndpoint, State state) {
    def agent = new ConsulAgent("${agentEndpoint}:${config.agentPort}", ConsulProperties.DEFAULT_TIMEOUT_MILLIS)

    // Enabling maintenance mode means the instance is removed from discovery & DNS lookups
    agent.api.maintenance(state == State.disable, "Spinnaker ${state} Operation")
  }
}
