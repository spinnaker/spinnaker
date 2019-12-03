/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
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

package com.netflix.spinnaker.halyard.config.validate.v1.providers.huaweicloud;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import java.util.List;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode(callSuper = false)
public class HuaweiCloudAccountValidator extends Validator<HuaweiCloudAccount> {

  @Override
  public void validate(ConfigProblemSetBuilder psBuilder, HuaweiCloudAccount account) {
    String accountName = account.getNodeName();

    DaemonTaskHandler.message(
        "Validating " + accountName + " with " + HuaweiCloudAccountValidator.class.getSimpleName());

    String authUrl = account.getAuthUrl();
    if (StringUtils.isEmpty(authUrl)) {
      psBuilder.addProblem(
          Problem.Severity.ERROR,
          String.format("For account: %s, you must provide an identity url.", accountName));
    }

    String username = account.getUsername();
    String password = account.getPassword();
    if (StringUtils.isEmpty(password) || StringUtils.isEmpty(username)) {
      psBuilder.addProblem(
          Problem.Severity.ERROR,
          String.format(
              "For account: %s, you must provide both username and password.", accountName));
    }

    String projectName = account.getProjectName();
    if (StringUtils.isEmpty(projectName)) {
      psBuilder.addProblem(
          Problem.Severity.ERROR,
          String.format("For account: %s, you must provide a project name.", accountName));
    }

    String domainName = account.getDomainName();
    if (StringUtils.isEmpty(domainName)) {
      psBuilder.addProblem(
          Problem.Severity.ERROR,
          String.format("For account: %s, you must provide a domain name.", accountName));
    }

    List<String> regions = account.getRegions();
    if (regions.size() == 0) {
      psBuilder.addProblem(
          Problem.Severity.ERROR,
          String.format("For account: %s, you must provide at least one region.", accountName));
    }

    Boolean insecure = account.getInsecure();
    if (insecure) {
      psBuilder.addProblem(
          Problem.Severity.WARNING,
          String.format(
              "For account: %s, you've chosen to not validate SSL connections. This setup is not recommended in production deployments.",
              accountName));
    }
  }
}
