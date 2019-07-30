/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.validate.v1.canary.prometheus;

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.ERROR;

import com.netflix.spinnaker.halyard.config.model.v1.canary.prometheus.PrometheusCanaryAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.validate.v1.canary.CanaryAccountValidator;
import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Data
@EqualsAndHashCode(callSuper = false)
@Component
public class PrometheusCanaryAccountValidator
    extends CanaryAccountValidator<PrometheusCanaryAccount> {

  @Autowired private SecretSessionManager secretSessionManager;

  @Override
  public void validate(ConfigProblemSetBuilder p, PrometheusCanaryAccount n) {
    super.validate(p, n);

    DaemonTaskHandler.message(
        "Validating "
            + n.getNodeName()
            + " with "
            + PrometheusCanaryAccountValidator.class.getSimpleName());

    String usernamePasswordFile = n.getUsernamePasswordFile();

    if (StringUtils.isNotEmpty(usernamePasswordFile)) {
      String usernamePassword = validatingFileDecrypt(p, usernamePasswordFile);

      if (StringUtils.isEmpty(usernamePassword)) {
        p.addProblem(ERROR, "The supplied username password file does not exist or is empty.")
            .setRemediation("Supply a valid username password file.");
      } else if (!usernamePassword.contains(":")) {
        p.addProblem(ERROR, "The supplied username password file does not contain a ':'.")
            .setRemediation("Supply a username password file containing \"username:password\".");
      }
    }
  }
}
