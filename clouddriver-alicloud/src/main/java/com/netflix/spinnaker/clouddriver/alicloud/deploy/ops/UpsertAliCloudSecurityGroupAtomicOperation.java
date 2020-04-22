/*
 * Copyright 2019 Alibaba Group.
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

package com.netflix.spinnaker.clouddriver.alicloud.deploy.ops;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.ecs.model.v20140526.*;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupAttributeResponse.Permission;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse.SecurityGroup;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.alicloud.common.ClientFactory;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.UpsertAliCloudSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.alicloud.exception.AliCloudException;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import groovy.util.logging.Slf4j;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class UpsertAliCloudSecurityGroupAtomicOperation implements AtomicOperation<Void> {

  private final Logger log =
      LoggerFactory.getLogger(UpsertAliCloudSecurityGroupAtomicOperation.class);

  private final UpsertAliCloudSecurityGroupDescription description;

  private final ClientFactory clientFactory;

  private final ObjectMapper objectMapper;

  public UpsertAliCloudSecurityGroupAtomicOperation(
      UpsertAliCloudSecurityGroupDescription description,
      ClientFactory clientFactory,
      ObjectMapper objectMapper) {
    this.description = description;
    this.clientFactory = clientFactory;
    this.objectMapper = objectMapper;
  }

  @Override
  public Void operate(List priorOutputs) {
    IAcsClient client =
        clientFactory.createClient(
            description.getRegion(),
            description.getCredentials().getAccessKeyId(),
            description.getCredentials().getAccessSecretKey());
    DescribeSecurityGroupsRequest describeSecurityGroupsRequest =
        new DescribeSecurityGroupsRequest();
    describeSecurityGroupsRequest.setSecurityGroupName(description.getSecurityGroupName());
    describeSecurityGroupsRequest.setPageSize(50);
    DescribeSecurityGroupsResponse describeSecurityGroupsResponse;
    try {
      describeSecurityGroupsResponse = client.getAcsResponse(describeSecurityGroupsRequest);
      List<SecurityGroup> securityGroups = describeSecurityGroupsResponse.getSecurityGroups();
      if (securityGroups.size() == 0) {
        CreateSecurityGroupRequest createSecurityGroupRequest =
            objectMapper.convertValue(description, CreateSecurityGroupRequest.class);
        CreateSecurityGroupResponse createSecurityGroupResponse =
            client.getAcsResponse(createSecurityGroupRequest);
        SecurityGroup securityGroup = new SecurityGroup();
        securityGroup.setSecurityGroupId(createSecurityGroupResponse.getSecurityGroupId());
        securityGroups.add(securityGroup);
      }

      buildIngressRule(client, securityGroups.get(0).getSecurityGroupId());

    } catch (ServerException e) {
      log.info(e.getMessage());
      throw new AliCloudException(e.getMessage());
    } catch (ClientException e) {
      log.info(e.getMessage());
      throw new AliCloudException(e.getMessage());
    }

    return null;
  }

  private void buildIngressRule(IAcsClient client, String securityGroupId)
      throws ClientException, ServerException {
    DescribeSecurityGroupAttributeRequest securityGroupAttributeRequest =
        new DescribeSecurityGroupAttributeRequest();
    securityGroupAttributeRequest.setSecurityGroupId(securityGroupId);
    securityGroupAttributeRequest.setDirection("ingress");
    DescribeSecurityGroupAttributeResponse securityGroupAttribute =
        client.getAcsResponse(securityGroupAttributeRequest);

    // If the incoming security rule is empty, delete all security rules under the current security
    // group
    if (description.getSecurityGroupIngress() == null
        || description.getSecurityGroupIngress().size() == 0) {
      for (Permission permission : securityGroupAttribute.getPermissions()) {
        RevokeSecurityGroupRequest revokeSecurityGroupRequest =
            objectMapper.convertValue(permission, RevokeSecurityGroupRequest.class);
        revokeSecurityGroupRequest.setSecurityGroupId(securityGroupId);
        client.getAcsResponse(revokeSecurityGroupRequest);
      }
      return;
    }

    // At this point, it means that security rules have not been added to the security group, then
    // all incoming rules are added.
    if (securityGroupAttribute.getPermissions().size() == 0) {
      // create security ingress rule
      if (description.getSecurityGroupIngress() != null) {
        for (AuthorizeSecurityGroupRequest securityGroupIngress :
            description.getSecurityGroupIngress()) {
          securityGroupIngress.setSecurityGroupId(securityGroupId);
          client.getAcsResponse(securityGroupIngress);
        }
      }
      return;
    }

    // Data to be modified
    List<AuthorizeSecurityGroupRequest> updateList = new ArrayList<>();

    // Open Contrast Logic
    // After filtering, the remaining elements in this collection are data that needs to be deleted
    Iterator<Permission> permissions = securityGroupAttribute.getPermissions().iterator();

    while (permissions.hasNext()) {
      Permission permission = permissions.next();
      // After filtering, the remaining elements in the subset are the data that needs to be added.
      Iterator<AuthorizeSecurityGroupRequest> securityGroupIngress =
          description.getSecurityGroupIngress().iterator();
      while (securityGroupIngress.hasNext()) {
        AuthorizeSecurityGroupRequest ingress = securityGroupIngress.next();
        if (compareMethod(permission, ingress)) {
          updateList.add(ingress);
          securityGroupIngress.remove();
          permissions.remove();
          break;
        }
      }
    }

    for (Permission permission : securityGroupAttribute.getPermissions()) {
      RevokeSecurityGroupRequest revokeSecurityGroupRequest =
          objectMapper.convertValue(permission, RevokeSecurityGroupRequest.class);
      revokeSecurityGroupRequest.setSecurityGroupId(securityGroupId);
      client.getAcsResponse(revokeSecurityGroupRequest);
    }

    for (AuthorizeSecurityGroupRequest securityGroupIngress :
        description.getSecurityGroupIngress()) {
      securityGroupIngress.setSecurityGroupId(securityGroupId);
      client.getAcsResponse(securityGroupIngress);
    }

    for (AuthorizeSecurityGroupRequest authorizeSecurityGroupRequest : updateList) {
      ModifySecurityGroupRuleRequest modifySecurityGroupRuleRequest =
          objectMapper.convertValue(
              authorizeSecurityGroupRequest, ModifySecurityGroupRuleRequest.class);
      modifySecurityGroupRuleRequest.setSecurityGroupId(securityGroupId);
      client.getAcsResponse(modifySecurityGroupRuleRequest);
    }
  }

  private boolean compareMethod(Permission permission, AuthorizeSecurityGroupRequest ingress) {
    if (StringUtils.isNotEmpty(permission.getSourceCidrIp())
        && StringUtils.isNotEmpty(ingress.getSourceCidrIp())) {
      CidrIp cidrIp1 = new CidrIp();
      cidrIp1.setIpProtocol(
          StringUtils.isNotEmpty(permission.getIpProtocol())
              ? permission.getIpProtocol().toLowerCase()
              : permission.getIpProtocol());
      cidrIp1.setPortRange(permission.getPortRange());
      cidrIp1.setSourcePortRange(permission.getSourcePortRange());
      cidrIp1.setNicType(permission.getNicType());
      cidrIp1.setPolicy(
          StringUtils.isNotEmpty(permission.getPolicy())
              ? permission.getPolicy().toLowerCase()
              : permission.getPolicy());
      cidrIp1.setDestCidrIp(permission.getDestCidrIp());
      cidrIp1.setSourceCidrIp(permission.getSourceCidrIp());

      CidrIp cidrIp2 = new CidrIp();
      cidrIp2.setIpProtocol(
          StringUtils.isNotEmpty(ingress.getIpProtocol())
              ? ingress.getIpProtocol().toLowerCase()
              : ingress.getIpProtocol());
      cidrIp2.setPortRange(ingress.getPortRange());
      cidrIp2.setSourcePortRange(ingress.getSourcePortRange());
      cidrIp2.setNicType(ingress.getNicType());
      cidrIp2.setPolicy(
          StringUtils.isNotEmpty(ingress.getPolicy())
              ? ingress.getPolicy().toLowerCase()
              : ingress.getPolicy());
      cidrIp2.setDestCidrIp(ingress.getDestCidrIp());
      cidrIp2.setSourceCidrIp(ingress.getSourceCidrIp());
      return cidrIp1.equals(cidrIp2);
    }

    if (StringUtils.isNotEmpty(permission.getSourceGroupId())
        && StringUtils.isNotEmpty(ingress.getSecurityGroupId())) {
      GroupId groupId1 = new GroupId();
      groupId1.setIpProtocol(
          StringUtils.isNotEmpty(permission.getIpProtocol())
              ? permission.getIpProtocol().toLowerCase()
              : permission.getIpProtocol());
      groupId1.setPortRange(permission.getPortRange());
      groupId1.setSourcePortRange(permission.getSourcePortRange());
      groupId1.setNicType(permission.getNicType());
      groupId1.setPolicy(
          StringUtils.isNotEmpty(permission.getPolicy())
              ? permission.getPolicy().toLowerCase()
              : permission.getPolicy());
      groupId1.setDestCidrIp(permission.getDestCidrIp());
      groupId1.setSourceGroupOwnerAccount(permission.getSourceGroupOwnerAccount());
      groupId1.setSourceGroupId(permission.getSourceGroupId());

      GroupId groupId2 = new GroupId();
      groupId2.setIpProtocol(
          StringUtils.isNotEmpty(ingress.getIpProtocol())
              ? ingress.getIpProtocol().toLowerCase()
              : ingress.getIpProtocol());
      groupId2.setPortRange(ingress.getPortRange());
      groupId2.setSourcePortRange(ingress.getSourcePortRange());
      groupId2.setNicType(ingress.getNicType());
      groupId2.setPolicy(
          StringUtils.isNotEmpty(ingress.getPolicy())
              ? ingress.getPolicy().toLowerCase()
              : ingress.getPolicy());
      groupId2.setDestCidrIp(ingress.getDestCidrIp());
      groupId2.setSourceGroupOwnerAccount(ingress.getSourceGroupOwnerAccount());
      groupId2.setSourceGroupId(ingress.getSourceGroupId());
    }

    return false;
  }

  public static class CidrIp {
    private String ipProtocol;
    private String portRange;
    private String sourcePortRange;
    private String nicType;
    private String policy;
    private String destCidrIp;
    private String sourceCidrIp;

    public String getIpProtocol() {
      return ipProtocol;
    }

    public void setIpProtocol(String ipProtocol) {
      this.ipProtocol = ipProtocol;
    }

    public String getPortRange() {
      return portRange;
    }

    public void setPortRange(String portRange) {
      this.portRange = portRange;
    }

    public String getSourcePortRange() {
      return sourcePortRange;
    }

    public void setSourcePortRange(String sourcePortRange) {
      this.sourcePortRange = sourcePortRange;
    }

    public String getNicType() {
      return nicType;
    }

    public void setNicType(String nicType) {
      this.nicType = nicType;
    }

    public String getPolicy() {
      return policy;
    }

    public void setPolicy(String policy) {
      this.policy = policy;
    }

    public String getDestCidrIp() {
      return destCidrIp;
    }

    public void setDestCidrIp(String destCidrIp) {
      this.destCidrIp = destCidrIp;
    }

    public String getSourceCidrIp() {
      return sourceCidrIp;
    }

    public void setSourceCidrIp(String sourceCidrIp) {
      this.sourceCidrIp = sourceCidrIp;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CidrIp cidrIp = (CidrIp) o;
      return Objects.equals(ipProtocol, cidrIp.ipProtocol)
          && Objects.equals(portRange, cidrIp.portRange)
          && Objects.equals(sourcePortRange, cidrIp.sourcePortRange)
          && Objects.equals(nicType, cidrIp.nicType)
          && Objects.equals(policy, cidrIp.policy)
          && Objects.equals(destCidrIp, cidrIp.destCidrIp)
          && Objects.equals(sourceCidrIp, cidrIp.sourceCidrIp);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          ipProtocol, portRange, sourcePortRange, nicType, policy, destCidrIp, sourceCidrIp);
    }
  }

  public static class GroupId {
    private String ipProtocol;
    private String portRange;
    private String sourcePortRange;
    private String nicType;
    private String policy;
    private String destCidrIp;
    private String sourceGroupOwnerAccount;
    private String sourceGroupId;

    public String getIpProtocol() {
      return ipProtocol;
    }

    public void setIpProtocol(String ipProtocol) {
      this.ipProtocol = ipProtocol;
    }

    public String getPortRange() {
      return portRange;
    }

    public void setPortRange(String portRange) {
      this.portRange = portRange;
    }

    public String getSourcePortRange() {
      return sourcePortRange;
    }

    public void setSourcePortRange(String sourcePortRange) {
      this.sourcePortRange = sourcePortRange;
    }

    public String getNicType() {
      return nicType;
    }

    public void setNicType(String nicType) {
      this.nicType = nicType;
    }

    public String getPolicy() {
      return policy;
    }

    public void setPolicy(String policy) {
      this.policy = policy;
    }

    public String getDestCidrIp() {
      return destCidrIp;
    }

    public void setDestCidrIp(String destCidrIp) {
      this.destCidrIp = destCidrIp;
    }

    public String getSourceGroupOwnerAccount() {
      return sourceGroupOwnerAccount;
    }

    public void setSourceGroupOwnerAccount(String sourceGroupOwnerAccount) {
      this.sourceGroupOwnerAccount = sourceGroupOwnerAccount;
    }

    public String getSourceGroupId() {
      return sourceGroupId;
    }

    public void setSourceGroupId(String sourceGroupId) {
      this.sourceGroupId = sourceGroupId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      GroupId groupId = (GroupId) o;
      return Objects.equals(ipProtocol, groupId.ipProtocol)
          && Objects.equals(portRange, groupId.portRange)
          && Objects.equals(sourcePortRange, groupId.sourcePortRange)
          && Objects.equals(nicType, groupId.nicType)
          && Objects.equals(policy, groupId.policy)
          && Objects.equals(destCidrIp, groupId.destCidrIp)
          && Objects.equals(sourceGroupOwnerAccount, groupId.sourceGroupOwnerAccount)
          && Objects.equals(sourceGroupId, groupId.sourceGroupId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          ipProtocol,
          portRange,
          sourcePortRange,
          nicType,
          policy,
          destCidrIp,
          sourceGroupOwnerAccount,
          sourceGroupId);
    }
  }
}
