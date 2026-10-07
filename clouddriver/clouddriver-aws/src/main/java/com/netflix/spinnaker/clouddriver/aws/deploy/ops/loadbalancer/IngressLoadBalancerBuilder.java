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

import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupIngressConverter;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.ec2.model.UserIdGroupPair;

@Component
public class IngressLoadBalancerBuilder {

  public IngressLoadBalancerGroupResult ingressApplicationLoadBalancerGroup(
      String application,
      String region,
      String credentialAccount,
      NetflixAmazonCredentials credentials,
      String vpcId,
      Collection<Integer> ports,
      SecurityGroupLookupFactory securityGroupLookupFactory)
      throws FailedSecurityGroupIngressException {
    SecurityGroupLookupFactory.SecurityGroupLookup securityGroupLookup =
        securityGroupLookupFactory.getInstance(region);

    // 1. get app load balancer security group & app security group. create if doesn't exist
    SecurityGroupLookupFactory.SecurityGroupUpdater applicationLoadBalancerSecurityGroupUpdater =
        getOrCreateSecurityGroup(
            application + "-elb",
            region,
            "Application ELB Security Group for " + application,
            credentialAccount,
            credentials,
            vpcId,
            securityGroupLookup);

    SecurityGroupLookupFactory.SecurityGroupUpdater applicationSecurityGroupUpdater =
        getOrCreateSecurityGroup(
            application,
            region,
            "Application Security Group for " + application,
            credentialAccount,
            credentials,
            vpcId,
            securityGroupLookup);

    SecurityGroup source = applicationLoadBalancerSecurityGroupUpdater.getSecurityGroup();
    SecurityGroup target = applicationSecurityGroupUpdater.getSecurityGroup();
    List<IpPermission> currentPermissions =
        SecurityGroupIngressConverter.flattenPermissions(target);
    List<IpPermission> targetPermissions =
        ports.stream()
            .map(port -> newIpPermissionWithSourceAndPort(source.groupId(), port))
            .collect(Collectors.toList());

    filterOutExistingPermissions(targetPermissions, currentPermissions);
    if (!targetPermissions.isEmpty()) {
      try {
        applicationSecurityGroupUpdater.addIngress(targetPermissions);
      } catch (Exception e) {
        throw new FailedSecurityGroupIngressException(e);
      }
    }

    return new IngressLoadBalancerGroupResult(source.groupId(), source.groupName());
  }

  private SecurityGroupLookupFactory.SecurityGroupUpdater getOrCreateSecurityGroup(
      String groupName,
      String region,
      String descriptionText,
      String credentialAccount,
      NetflixAmazonCredentials credentials,
      String vpcId,
      SecurityGroupLookupFactory.SecurityGroupLookup securityGroupLookup) {
    return (SecurityGroupLookupFactory.SecurityGroupUpdater)
        OperationPoller.retryWithBackoff(
            o -> {
              SecurityGroupLookupFactory.SecurityGroupUpdater securityGroupUpdater =
                  securityGroupLookup
                      .getSecurityGroupByName(credentialAccount, groupName, vpcId)
                      .orElse(null);

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
            },
            500,
            3);
  }

  private void filterOutExistingPermissions(
      List<IpPermission> permissionsToAdd, List<IpPermission> existingPermissions) {
    ListIterator<IpPermission> it = permissionsToAdd.listIterator();
    while (it.hasNext()) {
      IpPermission permission = it.next();
      List<UserIdGroupPair> filteredPairs =
          permission.userIdGroupPairs().stream()
              .filter(
                  pair ->
                      existingPermissions.stream()
                          .noneMatch(
                              p ->
                                  p.fromPort().equals(permission.fromPort())
                                      && p.toPort().equals(permission.toPort())
                                      && pair.groupId() != null
                                      && p.userIdGroupPairs().stream()
                                          .anyMatch(
                                              gp ->
                                                  gp.groupId() != null
                                                      && gp.groupId().equals(pair.groupId()))))
              .collect(Collectors.toList());

      if (filteredPairs.isEmpty()) {
        it.remove();
      } else {
        it.set(permission.toBuilder().userIdGroupPairs(filteredPairs).build());
      }
    }
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
    return IpPermission.builder()
        .ipProtocol("tcp")
        .fromPort(port)
        .toPort(port)
        .userIdGroupPairs(UserIdGroupPair.builder().groupId(sourceGroupId).build())
        .build();
  }

  static class FailedSecurityGroupIngressException extends Exception {
    FailedSecurityGroupIngressException(Exception e) {
      super(e);
    }
  }
}
