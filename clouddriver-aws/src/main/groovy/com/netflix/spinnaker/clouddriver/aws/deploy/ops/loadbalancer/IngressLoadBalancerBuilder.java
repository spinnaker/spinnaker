/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer;

import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.UserIdGroupPair;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupIngressConverter;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class IngressLoadBalancerBuilder {

  public IngressLoadBalancerGroupResult ingressApplicationLoadBalancerGroup(String application,
                                                                            String region,
                                                                            String credentialAccount,
                                                                            NetflixAmazonCredentials credentials,
                                                                            String vpcId,
                                                                            Collection<Integer> ports,
                                                                            SecurityGroupLookupFactory securityGroupLookupFactory) throws FailedSecurityGroupIngressException {
    SecurityGroupLookupFactory.SecurityGroupLookup securityGroupLookup = securityGroupLookupFactory.getInstance(region);

    // 1. get app load balancer security group & app security group. create if doesn't exist
    SecurityGroupLookupFactory.SecurityGroupUpdater applicationLoadBalancerSecurityGroupUpdater = getOrCreateSecurityGroup(
      application + "-elb",
      region,
      "Application ELB Security Group for " + application,
      credentialAccount,
      credentials,
      vpcId,
      securityGroupLookup
    );

    SecurityGroupLookupFactory.SecurityGroupUpdater applicationSecurityGroupUpdater = getOrCreateSecurityGroup(
      application,
      region,
      "Application Security Group for " + application,
      credentialAccount,
      credentials,
      vpcId,
      securityGroupLookup
    );

    SecurityGroup source = applicationLoadBalancerSecurityGroupUpdater.getSecurityGroup();
    SecurityGroup target = applicationSecurityGroupUpdater.getSecurityGroup();
    List<IpPermission> currentPermissions = SecurityGroupIngressConverter.flattenPermissions(target);
    List<IpPermission> targetPermissions = ports.stream().map(port ->
      newIpPermissionWithSourceAndPort(source.getGroupId(), port)
    ).collect(Collectors.toList());

    filterOutExistingPermissions(targetPermissions, currentPermissions);
    if (!targetPermissions.isEmpty()) {
      try {
        applicationSecurityGroupUpdater.addIngress(targetPermissions);
      } catch (Exception e) {
        throw new FailedSecurityGroupIngressException(e);
      }
    }

    return new IngressLoadBalancerGroupResult(source.getGroupId(), source.getGroupName());
  }

  private SecurityGroupLookupFactory.SecurityGroupUpdater getOrCreateSecurityGroup(String groupName,
                                                                                   String region,
                                                                                   String descriptionText,
                                                                                   String credentialAccount,
                                                                                   NetflixAmazonCredentials credentials,
                                                                                   String vpcId,
                                                                                   SecurityGroupLookupFactory.SecurityGroupLookup securityGroupLookup) {
    return (SecurityGroupLookupFactory.SecurityGroupUpdater) OperationPoller.retryWithBackoff(o -> {
      SecurityGroupLookupFactory.SecurityGroupUpdater securityGroupUpdater = securityGroupLookup.getSecurityGroupByName(
        credentialAccount,
        groupName,
        vpcId
      ).orElse(null);

      if (securityGroupUpdater == null) {
        UpsertSecurityGroupDescription description = new UpsertSecurityGroupDescription();
        description.setName(groupName);
        description.setDescription(descriptionText);
        description.setVpcId(vpcId);
        description.setRegion(region);
        description.setCredentials(credentials);
        return securityGroupLookup.createSecurityGroup(description);
      }
      return securityGroupUpdater;
    }, 500, 3);

  }

  private void filterOutExistingPermissions(List<IpPermission> permissionsToAdd,
                                            List<IpPermission> existingPermissions) {
    permissionsToAdd.forEach(permission ->
      permission.getUserIdGroupPairs().removeIf(pair ->
        existingPermissions.stream().anyMatch(p ->
          p.getFromPort().equals(permission.getFromPort()) &&
            p.getToPort().equals(permission.getToPort()) &&
            pair.getGroupId() != null &&
            p.getUserIdGroupPairs().stream()
              .anyMatch(gp -> gp.getGroupId() != null && gp.getGroupId().equals(pair.getGroupId()))
        )
      )
    );

    permissionsToAdd.removeIf(permission -> permission.getUserIdGroupPairs().isEmpty());
  }

  public static class IngressLoadBalancerGroupResult {
    public final String groupId;
    public final String groupName;

    IngressLoadBalancerGroupResult(String groupId, String groupName) {
      this.groupId = groupId;
      this.groupName = groupName;
    }

  }

  private IpPermission newIpPermissionWithSourceAndPort(String sourceGroupId, int port) {
    return new IpPermission()
      .withIpProtocol("tcp")
      .withFromPort(port)
      .withToPort(port)
      .withUserIdGroupPairs(new UserIdGroupPair().withGroupId(sourceGroupId));
  }

  static class FailedSecurityGroupIngressException extends Exception {
    FailedSecurityGroupIngressException(Exception e) {
      super(e);
    }
  }
}
