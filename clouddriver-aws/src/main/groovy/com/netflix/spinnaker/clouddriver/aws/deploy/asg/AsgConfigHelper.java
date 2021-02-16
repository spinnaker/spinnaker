/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.asg;

import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDateTime;

/** A helper class for utility methods related to {@link AutoScalingWorker.AsgConfiguration} */
@Slf4j
public class AsgConfigHelper {

  public static String createName(String baseName, String suffix) {
    StringBuilder name = new StringBuilder(baseName);
    if (StringUtils.isNotEmpty(suffix)) {
      name.append('-').append(suffix);
    } else {
      name.append('-').append(createDefaultSuffix());
    }
    return name.toString();
  }

  /**
   * Set resolved security groups for an application.
   *
   * @param asgConfig Asg Configuration to work with
   * @param securityGroupService SecurityGroup service
   * @param deployDefaults defaults
   * @return asgConfig with resolved security groups and classicLinkVpcSecurityGroups
   */
  public static AutoScalingWorker.AsgConfiguration setAppSecurityGroups(
      AutoScalingWorker.AsgConfiguration asgConfig,
      SecurityGroupService securityGroupService,
      DeployDefaults deployDefaults) {

    // resolve security group ids and names in request
    List<String> securityGroupIds =
        securityGroupService.resolveSecurityGroupIdsWithSubnetType(
            asgConfig.getSecurityGroups(), asgConfig.getSubnetType());

    // conditionally, find or create an application security group
    if ((securityGroupIds == null || securityGroupIds.isEmpty())
        || (deployDefaults.isAddAppGroupToServerGroup()
            && securityGroupIds.size() < deployDefaults.getMaxSecurityGroups())) {

      // get a mapping of security group names to ids and find an existing security group for
      // application
      final Map<String, String> names =
          securityGroupService.getSecurityGroupNamesFromIds(securityGroupIds);
      String existingAppGroup =
          (names != null && !names.isEmpty())
              ? names.keySet().stream()
                  .filter(it -> it.contains(asgConfig.getApplication()))
                  .findFirst()
                  .orElse(null)
              : null;

      // if no existing security group, find by subnet type / create a new security group for
      // application
      if (StringUtils.isEmpty(existingAppGroup)) {
        String applicationSecurityGroupId =
            (String)
                OperationPoller.retryWithBackoff(
                    o ->
                        createSecurityGroupForApp(
                            securityGroupService,
                            asgConfig.getApplication(),
                            asgConfig.getSubnetType()),
                    500,
                    3);
        securityGroupIds.add(applicationSecurityGroupId);
      }
    }
    asgConfig.setSecurityGroups(securityGroupIds.stream().distinct().collect(Collectors.toList()));

    if (asgConfig.getClassicLinkVpcSecurityGroups() != null
        && !asgConfig.getClassicLinkVpcSecurityGroups().isEmpty()) {
      if (StringUtils.isEmpty(asgConfig.getClassicLinkVpcId())) {
        throw new IllegalStateException(
            "Can't provide classic link security groups without classiclink vpc Id");
      }
      List<String> classicLinkIds =
          securityGroupService.resolveSecurityGroupIdsInVpc(
              asgConfig.getClassicLinkVpcSecurityGroups(), asgConfig.getClassicLinkVpcId());
      asgConfig.setClassicLinkVpcSecurityGroups(classicLinkIds);
    }

    return asgConfig;
  }

  private static String createSecurityGroupForApp(
      SecurityGroupService securityGroupService, String application, String subnetType) {

    // find security group by subnet type
    String applicationSecurityGroupId =
        securityGroupService.getSecurityGroupForApplication(application, subnetType);

    // conditionally, create security group
    if (StringUtils.isEmpty(applicationSecurityGroupId)) {
      log.debug("Creating security group for application {}", application);
      applicationSecurityGroupId =
          securityGroupService.createSecurityGroup(application, subnetType);
    }
    return applicationSecurityGroupId;
  }

  private static String createDefaultSuffix() {
    return new LocalDateTime().toString("MMddYYYYHHmmss");
  }
}
