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
import com.netflix.spinnaker.halyard.config.model.v1.providers.tencentcloud.TencentCloudBaseImage;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import lombok.EqualsAndHashCode;
import org.springframework.util.StringUtils;

@EqualsAndHashCode(callSuper = false)
public class TencentCloudBaseImageValidator extends Validator<TencentCloudBaseImage> {

  @Override
  public void validate(ConfigProblemSetBuilder p, TencentCloudBaseImage n) {
    String baseImage = n.getNodeName();
    DaemonTaskHandler.message(
        "Validating "
            + baseImage
            + " with "
            + TencentCloudBaseImageValidator.class.getSimpleName());

    TencentCloudBaseImage.TencentCloudVirtualizationSettings vs =
        n.getVirtualizationSettings().get(0);

    String region = vs.getRegion();
    if (StringUtils.isEmpty(region)) {
      p.addProblem(
          Problem.Severity.ERROR,
          String.format("For base image:%s, no region supplied.", baseImage));
    }

    String zone = vs.getZone();
    if (StringUtils.isEmpty(zone)) {
      p.addProblem(
          Problem.Severity.ERROR, String.format("For base image:%s, no zone supplied.", baseImage));
    }

    String instanceType = vs.getInstanceType();
    if (StringUtils.isEmpty(instanceType)) {
      p.addProblem(
          Problem.Severity.ERROR,
          String.format("For base image:%s, no instanceType supplied.", baseImage));
    }

    String sourceImageId = vs.getSourceImageId();
    if (StringUtils.isEmpty(sourceImageId)) {
      p.addProblem(
          Problem.Severity.ERROR,
          String.format("For base image:%s, no sourceImageId supplied.", baseImage));
    }

    String sshUsername = vs.getSshUserName();
    if (StringUtils.isEmpty(sshUsername)) {
      p.addProblem(
          Problem.Severity.ERROR,
          String.format("For base image:%s, no sshUsername supplied.", baseImage));
    }
  }
}
