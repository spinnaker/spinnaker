/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.huaweicloud.model;

import com.huawei.openstack4j.openstack.vpc.v1.domain.SecurityGroup;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HuaweiCloudSecurityGroupCacheData {
  private SecurityGroup securityGroup;

  /**
   * The remote security groups referenced by current security group rules. Key is the remote
   * security group id, and value is the cache data id of that security group.
   */
  private Map<String, String> relevantSecurityGroups;
}
