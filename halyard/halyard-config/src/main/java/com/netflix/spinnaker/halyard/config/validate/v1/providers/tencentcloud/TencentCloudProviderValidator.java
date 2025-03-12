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
import com.netflix.spinnaker.halyard.config.model.v1.providers.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import org.springframework.stereotype.Component;

@Component
public class TencentCloudProviderValidator extends Validator<TencentCloudProvider> {

  @Override
  public void validate(ConfigProblemSetBuilder p, TencentCloudProvider n) {
    TencentCloudAccountValidator accountValidator = new TencentCloudAccountValidator();

    n.getAccounts().forEach(account -> accountValidator.validate(p, account));

    TencentCloudBakeryDefaultsValidator bakeryValidator = new TencentCloudBakeryDefaultsValidator();

    bakeryValidator.validate(p, n.getBakeryDefaults());
  }
}
