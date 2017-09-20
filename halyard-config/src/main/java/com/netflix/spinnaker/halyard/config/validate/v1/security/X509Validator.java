/*
 * Copyright 2017 Target, Inc.
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
import com.netflix.spinnaker.halyard.config.model.v1.security.X509;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.springframework.stereotype.Component;

@Component
public class X509Validator extends Validator<X509> {

  @Override
  public void validate(ConfigProblemSetBuilder p, X509 x509) {
    if (!x509.isEnabled()) {
      return;
    }

    if (x509.getRoleOid() != null && (x509.getRoleOid() == "")) {
      p.addProblem(Problem.Severity.ERROR, "roleOid specified but given blank line");
    }
  }
}
