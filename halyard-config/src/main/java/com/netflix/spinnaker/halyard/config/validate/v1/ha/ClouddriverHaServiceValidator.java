/*
 * Copyright 2018 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.config.validate.v1.ha;

import com.netflix.spinnaker.halyard.config.model.v1.ha.ClouddriverHaService;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.apache.commons.lang3.StringUtils;

public class ClouddriverHaServiceValidator extends Validator<ClouddriverHaService> {
  @Override
  public void validate(ConfigProblemSetBuilder p, ClouddriverHaService clouddriverHaService) {
    boolean redisMasterEndpointIsBlank =
        StringUtils.isBlank(clouddriverHaService.getRedisMasterEndpoint());
    boolean redisSlaveEndpointIsBlank =
        StringUtils.isBlank(clouddriverHaService.getRedisSlaveEndpoint());
    boolean redisSlaveDeckEndpointIsBlank =
        StringUtils.isBlank(clouddriverHaService.getRedisSlaveDeckEndpoint());

    if (clouddriverHaService.isDisableClouddriverRoDeck()) {
      if (redisMasterEndpointIsBlank && redisSlaveEndpointIsBlank) {
        return;
      }
      if (redisMasterEndpointIsBlank || redisSlaveEndpointIsBlank) {
        p.addProblem(
            Problem.Severity.ERROR,
            "Please provide values for Clouddriver Redis master endpoint and Redis slave endpoint, or leave them both blank.");
      }
    } else {
      if (redisMasterEndpointIsBlank
          && redisSlaveEndpointIsBlank
          && redisSlaveDeckEndpointIsBlank) {
        return;
      }
      if (redisMasterEndpointIsBlank
          || redisSlaveEndpointIsBlank
          || redisSlaveDeckEndpointIsBlank) {
        p.addProblem(
            Problem.Severity.ERROR,
            "Please provide values for Clouddriver Redis master endpoint, Redis slave endpoint, and Redis slave-deck endpoint or leave them all blank.");
      }
    }
  }
}
