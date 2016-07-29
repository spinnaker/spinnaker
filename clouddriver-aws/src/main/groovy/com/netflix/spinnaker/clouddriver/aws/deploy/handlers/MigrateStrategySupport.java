/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.handlers;

import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.UserIdGroupPair;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;

import java.util.Collections;

public interface MigrateStrategySupport {

  default void addClassicLinkIngress(SecurityGroupLookup lookup, String classicLinkGroupName, String groupId, NetflixAmazonCredentials credentials, String vpcId) {
    if (classicLinkGroupName == null) {
      return;
    }
    lookup.getSecurityGroupById(credentials.getName(), groupId, vpcId).ifPresent(targetGroupUpdater -> {
      SecurityGroup targetGroup = targetGroupUpdater.getSecurityGroup();
      lookup.getSecurityGroupByName(credentials.getName(), classicLinkGroupName, vpcId)
        .map(updater -> updater.getSecurityGroup().getGroupId())
        .ifPresent(classicLinkGroupId -> {
          // don't attach if there's already some rule already configured
          if (targetGroup.getIpPermissions().stream()
            .anyMatch(p -> p.getUserIdGroupPairs().stream()
              .anyMatch(p2 -> p2.getGroupId().equals(classicLinkGroupId)))) {
            return;
          }
          targetGroupUpdater.addIngress(Collections.singletonList(
            new IpPermission()
              .withIpProtocol("tcp").withFromPort(80).withToPort(65535)
              .withUserIdGroupPairs(
                new UserIdGroupPair()
                  .withUserId(credentials.getAccountId())
                  .withGroupId(classicLinkGroupId)
                  .withVpcId(vpcId)
              )
          ));
        });
    });
  }
}
