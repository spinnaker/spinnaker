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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;

public class ClouddriverHaServiceValidator extends Validator<ClouddriverHaService> {
  @Override
  public void validate(ConfigProblemSetBuilder p, ClouddriverHaService clouddriverHaService) {
    List<String> redisEndpoints = Stream.of(
        clouddriverHaService.getRedisMasterEndpoint(),
        clouddriverHaService.getRedisSlaveEndpoint(),
        clouddriverHaService.getRedisSlaveDeckEndpoint()
    ).filter(e -> !StringUtils.isBlank(e))
        .collect(Collectors.toList());

    if (redisEndpoints.size() != 3 && redisEndpoints.size() != 0) {
      p.addProblem(Problem.Severity.ERROR, "Please provide values for Clouddriver Redis master endpoint, Redis slave endpoint, and Redis slave-deck endpoint or leave them all blank.");
    }
  }
}
