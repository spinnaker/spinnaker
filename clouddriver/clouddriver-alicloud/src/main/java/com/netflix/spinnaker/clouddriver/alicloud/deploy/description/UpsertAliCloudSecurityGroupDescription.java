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

package com.netflix.spinnaker.clouddriver.alicloud.deploy.description;

import com.aliyuncs.ecs.model.v20140526.AuthorizeSecurityGroupEgressRequest;
import com.aliyuncs.ecs.model.v20140526.AuthorizeSecurityGroupRequest;
import java.util.List;
import lombok.Data;

@Data
public class UpsertAliCloudSecurityGroupDescription extends BaseAliCloudDescription {

  private Long resourceOwnerId;

  private String resourceOwnerAccount;

  private String clientToken;

  private String ownerAccount;

  private String description;

  private Long ownerId;

  private String securityGroupName;

  private String securityGroupType;

  private String resourceGroupId;

  private String vpcId;

  private List<Tag> tags;

  private List<AuthorizeSecurityGroupRequest> securityGroupIngress;

  private List<AuthorizeSecurityGroupEgressRequest> securityGroupEgress;

  @Data
  public static class Tag {

    private String value;

    private String key;
  }
}
