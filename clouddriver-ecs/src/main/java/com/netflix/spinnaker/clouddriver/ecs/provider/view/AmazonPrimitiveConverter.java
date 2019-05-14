/*
 * Copyright 2018 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.netflix.spinnaker.clouddriver.aws.model.AmazonSecurityGroup;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonSubnet;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsSecurityGroup;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsSubnet;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AmazonPrimitiveConverter {

  private final EcsAccountMapper accountMapper;

  @Autowired
  public AmazonPrimitiveConverter(EcsAccountMapper accountMapper) {
    this.accountMapper = accountMapper;
  }

  public Collection<EcsSecurityGroup> convertToEcsSecurityGroup(
      Collection<AmazonSecurityGroup> securityGroups) {
    Collection<EcsSecurityGroup> convertedSecurityGroups = new HashSet<>();

    for (AmazonSecurityGroup securityGroup : securityGroups) {
      EcsSecurityGroup convertedSecurityGroup = convertToEcsSecurityGroup(securityGroup);
      if (convertedSecurityGroup != null) {
        convertedSecurityGroups.add(convertedSecurityGroup);
      }
    }

    return convertedSecurityGroups;
  }

  public EcsSecurityGroup convertToEcsSecurityGroup(AmazonSecurityGroup securityGroup) {
    NetflixECSCredentials account =
        accountMapper.fromAwsAccountNameToEcs(securityGroup.getAccountName());
    if (account == null) {
      return null;
    }

    EcsSecurityGroup ecsSecurityGroup =
        new EcsSecurityGroup(
            securityGroup.getId(),
            securityGroup.getName(),
            securityGroup.getVpcId(),
            securityGroup.getDescription(),
            securityGroup.getApplication(),
            account.getName(),
            account.getAccountId(),
            securityGroup.getRegion(),
            securityGroup.getInboundRules(),
            securityGroup.getOutboundRules());

    return ecsSecurityGroup;
  }

  public Set<EcsSubnet> convertToEcsSubnet(Collection<AmazonSubnet> subnet) {
    Set<EcsSubnet> convertedSecurityGroups = new HashSet<>();

    for (AmazonSubnet securityGroup : subnet) {
      EcsSubnet convertedSecurityGroup = convertToEcsSubnet(securityGroup);

      Optional.ofNullable(convertToEcsSubnet(securityGroup))
          .ifPresent(convertedSecurityGroups::add);
    }

    return convertedSecurityGroups;
  }

  public EcsSubnet convertToEcsSubnet(AmazonSubnet subnet) {
    NetflixECSCredentials ecsAccount = accountMapper.fromAwsAccountNameToEcs(subnet.getAccount());
    if (ecsAccount == null) {
      return null;
    }

    EcsSubnet ecsSubnet =
        new EcsSubnet(
            subnet.getType(),
            subnet.getId(),
            subnet.getState(),
            subnet.getVpcId(),
            subnet.getCidrBlock(),
            subnet.getAvailableIpAddressCount(),
            ecsAccount.getName(),
            ecsAccount.getAccountId(),
            subnet.getRegion(),
            subnet.getAvailabilityZone(),
            subnet.getPurpose(),
            subnet.getTarget(),
            subnet.isDeprecated());

    return ecsSubnet;
  }
}
