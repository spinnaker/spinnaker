/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.validate.v1.security;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.security.GithubRoleProvider;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.apache.commons.lang3.StringUtils;

public class GithubRoleProviderValidator extends Validator<GithubRoleProvider> {
  @Override
  public void validate(ConfigProblemSetBuilder p, GithubRoleProvider provider) {
    if (StringUtils.isEmpty(provider.getOrganization())) {
      p.addProblem(Problem.Severity.ERROR, "No organization specified.");
    }

    if (StringUtils.isEmpty(provider.getAccessToken())) {
      p.addProblem(Problem.Severity.ERROR, "No access token specified.");
    }
  }
}
