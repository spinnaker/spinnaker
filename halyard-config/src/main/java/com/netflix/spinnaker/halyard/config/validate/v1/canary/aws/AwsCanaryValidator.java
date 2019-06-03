/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.halyard.config.validate.v1.canary.aws;

import com.netflix.spinnaker.halyard.config.model.v1.canary.aws.AwsCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.aws.AwsCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.util.CollectionUtils;

public class AwsCanaryValidator extends Validator<AwsCanaryServiceIntegration> {

  @Override
  public void validate(ConfigProblemSetBuilder p, AwsCanaryServiceIntegration n) {
    if (n.isS3Enabled()) {
      List<AwsCanaryAccount> accountsWithBucket =
          n.getAccounts().stream().filter(a -> a.getBucket() != null).collect(Collectors.toList());

      if (CollectionUtils.isEmpty(accountsWithBucket)) {
        p.addProblem(
            Problem.Severity.ERROR,
            "At least one AWS account must specify a bucket if S3 is enabled.");
      }
    }
  }
}
