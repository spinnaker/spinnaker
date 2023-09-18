/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.tencentcloud;

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.MortService;
import com.netflix.spinnaker.orca.clouddriver.MortService.SecurityGroup;
import com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup.SecurityGroupUpserter;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TencentCloudSecurityGroupUpserter
    implements SecurityGroupUpserter, CloudProviderAware {

  private final String cloudProvider = "tencentcloud";
  @Autowired private MortService mortService;

  @Override
  public OperationContext getOperationContext(StageExecution stage) {
    Map<String, Map<String, Object>> operationMap = new HashMap<>();
    operationMap.put(SecurityGroupUpserter.OPERATION, stage.getContext());

    List<Map> ops = new ArrayList<>(Collections.singletonList(operationMap));

    SecurityGroup group = new SecurityGroup();
    group.setName((String) stage.getContext().get("securityGroupName"));
    group.setRegion((String) stage.getContext().get("region"));
    group.setAccountName(getCredentials(stage));

    List<SecurityGroup> targets = new ArrayList<>();
    targets.add(group);

    Map<String, List<SecurityGroup>> targetsMap = new HashMap<>();
    targetsMap.put("targets", targets);

    SecurityGroupUpserter.OperationContext operationContext =
        new SecurityGroupUpserter.OperationContext();
    operationContext.setOperations(ops);
    operationContext.setExtraOutput(targetsMap);
    return operationContext;
  }

  public boolean isSecurityGroupUpserted(
      SecurityGroup upsertedSecurityGroup, StageExecution stage) {
    log.info("Enter tencentcloud isSecurityGroupUpserted with " + upsertedSecurityGroup);
    try {
      SecurityGroup securityGroup =
          mortService.getSecurityGroup(
              upsertedSecurityGroup.getAccountName(),
              cloudProvider,
              upsertedSecurityGroup.getName(),
              upsertedSecurityGroup.getRegion(),
              upsertedSecurityGroup.getVpcId());

      return upsertedSecurityGroup.getName().equals(securityGroup.getName());
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() != 404) {
        throw e;
      }
    }

    return false;
  }

  public final String getCloudProvider() {
    return cloudProvider;
  }
}
