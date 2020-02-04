/*
 * Copyright 2020 THL A29 Limited, a Tencent company.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.halyard.config.validate.v1.providers.tencentcloud;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.tencentcloud.TencentCloudAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import java.util.List;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

@EqualsAndHashCode(callSuper = false)
public class TencentCloudAccountValidator extends Validator<TencentCloudAccount> {

  @Override
  public void validate(ConfigProblemSetBuilder p, TencentCloudAccount account) {
    String accountName = account.getNodeName();

    DaemonTaskHandler.message(
        "Validating "
            + accountName
            + " with "
            + TencentCloudAccountValidator.class.getSimpleName());

    String secretId = account.getSecretId();
    String secretKey = account.getSecretKey();
    if (StringUtils.isEmpty(secretId) || StringUtils.isEmpty(secretKey)) {
      p.addProblem(
          Problem.Severity.ERROR,
          String.format(
              "For account: %s, you must provide both secretId and secretKey.", accountName));
    }

    List<String> regions = account.getRegions();
    if (CollectionUtils.isEmpty(regions)) {
      p.addProblem(
          Problem.Severity.ERROR,
          String.format("For account: %s, you must provide at least one region.", accountName));
    }
  }
}
