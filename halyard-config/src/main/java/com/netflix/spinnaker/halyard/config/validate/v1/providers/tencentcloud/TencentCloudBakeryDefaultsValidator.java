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
import com.netflix.spinnaker.halyard.config.model.v1.providers.tencentcloud.TencentCloudBakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.providers.tencentcloud.TencentCloudBaseImage;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import java.util.List;
import lombok.EqualsAndHashCode;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@EqualsAndHashCode(callSuper = false)
public class TencentCloudBakeryDefaultsValidator extends Validator<TencentCloudBakeryDefaults> {

  @Override
  public void validate(ConfigProblemSetBuilder p, TencentCloudBakeryDefaults n) {
    DaemonTaskHandler.message(
        "Validating "
            + n.getNodeName()
            + " with "
            + TencentCloudBakeryDefaultsValidator.class.getSimpleName());

    String secretId = n.getSecretId();
    String secretKey = n.getSecretKey();
    List<TencentCloudBaseImage> baseImages = n.getBaseImages();

    if (StringUtils.isEmpty(secretId)
        && StringUtils.isEmpty(secretKey)
        && CollectionUtils.isEmpty(baseImages)) {
      return;
    }

    if (StringUtils.isEmpty(secretId)) {
      p.addProblem(Problem.Severity.ERROR, "For bakery defaults: no secretId supplied.");
    }

    if (StringUtils.isEmpty(secretKey)) {
      p.addProblem(Problem.Severity.ERROR, "For bakery defaults: no secretKey supplied.");
    }

    TencentCloudBaseImageValidator baseImageValidator = new TencentCloudBaseImageValidator();
    baseImages.forEach(baseImage -> baseImageValidator.validate(p, baseImage));
  }
}
