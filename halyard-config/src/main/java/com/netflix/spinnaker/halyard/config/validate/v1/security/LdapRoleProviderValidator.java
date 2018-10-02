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
 */

package com.netflix.spinnaker.halyard.config.validate.v1.security;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.security.LdapRoleProvider;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import org.apache.commons.lang3.StringUtils;

public class LdapRoleProviderValidator extends Validator<LdapRoleProvider> {
  @Override
  public void validate(ConfigProblemSetBuilder p, LdapRoleProvider l) {
    if (l.getUrl() == null) {
      p.addProblem(Severity.ERROR, "No url specified.");
    }
    if (StringUtils.isEmpty(l.getManagerDn()) && !StringUtils.isEmpty(l.getManagerPassword())) {
      p.addProblem(Problem.Severity.ERROR, "No manager dn specified.");
    }
    if (StringUtils.isEmpty(l.getManagerPassword()) && !StringUtils.isEmpty(l.getManagerDn())) {
      p.addProblem(Problem.Severity.ERROR, "No manager password specified.");
    }
  }
}
