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
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudBakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudBaseImage;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import java.util.List;
import lombok.EqualsAndHashCode;
import org.springframework.util.StringUtils;

@EqualsAndHashCode(callSuper = false)
public class HuaweiCloudBakeryDefaultsValidator extends Validator<HuaweiCloudBakeryDefaults> {

  @Override
  public void validate(ConfigProblemSetBuilder p, HuaweiCloudBakeryDefaults n) {
    DaemonTaskHandler.message(
        "Validating "
            + n.getNodeName()
            + " with "
            + HuaweiCloudBakeryDefaultsValidator.class.getSimpleName());

    if ((new HuaweiCloudBakeryDefaults()).equals(n)) {
      return;
    }

    String authUrl = n.getAuthUrl();
    if (StringUtils.isEmpty(authUrl)) {
      p.addProblem(Problem.Severity.ERROR, "For bakery defaults: no auth url supplied.");
    }

    String username = n.getUsername();
    if (StringUtils.isEmpty(username)) {
      p.addProblem(Problem.Severity.ERROR, "For bakery defaults: no username supplied.");
    }

    String password = n.getPassword();
    if (StringUtils.isEmpty(password)) {
      p.addProblem(Problem.Severity.ERROR, "For bakery defaults: no password supplied.");
    }

    String projectName = n.getProjectName();
    if (StringUtils.isEmpty(projectName)) {
      p.addProblem(Problem.Severity.ERROR, "For bakery defaults: no project name supplied.");
    }

    String domainName = n.getDomainName();
    if (StringUtils.isEmpty(domainName)) {
      p.addProblem(Problem.Severity.ERROR, "For bakery defaults: no domain name supplied");
    }

    Boolean insecure = n.getInsecure();
    if (insecure) {
      p.addProblem(
          Problem.Severity.WARNING,
          "For bakery defaults: You've chosen to not validate SSL connections. This setup is not recommended in production deployments.");
    }

    String vpcId = n.getVpcId();
    if (StringUtils.isEmpty(vpcId)) {
      p.addProblem(Problem.Severity.ERROR, "For bakery defaults: no vpc id supplied.");
    }

    String subnetId = n.getSubnetId();
    if (StringUtils.isEmpty(subnetId)) {
      p.addProblem(Problem.Severity.ERROR, "For bakery defaults: no subnet id supplied.");
    }

    Integer eipBandwidthSize = n.getEipBandwidthSize();
    if (eipBandwidthSize <= 0) {
      p.addProblem(Problem.Severity.ERROR, "For bakery defaults: no eip bandwidth size supplied.");
    }

    String securityGroup = n.getSecurityGroup();
    if (StringUtils.isEmpty(securityGroup)) {
      p.addProblem(Problem.Severity.ERROR, "For bakery defaults: no security group supplied.");
    }

    HuaweiCloudBaseImageValidator huaweicloudBaseImageValidator =
        new HuaweiCloudBaseImageValidator();
    List<HuaweiCloudBaseImage> baseImages = n.getBaseImages();
    baseImages.forEach(
        huaweicloudBaseImage -> huaweicloudBaseImageValidator.validate(p, huaweicloudBaseImage));
  }
}
