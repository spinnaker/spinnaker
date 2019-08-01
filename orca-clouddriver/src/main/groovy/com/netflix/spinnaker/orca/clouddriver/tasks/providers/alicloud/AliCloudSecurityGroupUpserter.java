/*
 * Copyright 2019 Alibaba Group.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.alicloud;

import com.netflix.spinnaker.orca.clouddriver.MortService;
import com.netflix.spinnaker.orca.clouddriver.MortService.SecurityGroup;
import com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup.SecurityGroupUpserter;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AliCloudSecurityGroupUpserter implements SecurityGroupUpserter, CloudProviderAware {

  final String cloudProvider = "alicloud";

  @Autowired MortService mortService;

  @Override
  public OperationContext getOperationContext(Stage stage) {
    SecurityGroupUpserter.OperationContext operationContext =
        new SecurityGroupUpserter.OperationContext();
    Map<String, Object> context = stage.getContext();
    Map<String, Object> extraOutput = new HashMap(30);
    String name = (String) context.get("securityGroupName");
    String vpcId = (String) context.get("vpcId");
    Object securityGroupIngress = context.get("securityGroupIngress");
    List<Map> list = new ArrayList<>();
    List<MortService.SecurityGroup> targets = new ArrayList<>();
    List<String> regions = new ArrayList<>();
    if (context.get("regions") != null) {
      regions.addAll((List) context.get("regions"));
    } else {
      if (context.get("region") != null) {
        regions.add((String) context.get("region"));
      }
    }
    for (String region : regions) {
      Map<String, Object> map = new HashMap(16);
      Map<String, Object> operation = new HashMap(50);
      MortService.SecurityGroup securityGroup = new MortService.SecurityGroup();
      securityGroup.setAccountName(getCredentials(stage));
      securityGroup.setRegion(region);
      securityGroup.setName(name);
      securityGroup.setVpcId(vpcId);
      targets.add(securityGroup);
      operation.putAll(context);
      operation.put("region", region);
      map.put(SecurityGroupUpserter.OPERATION, operation);
      list.add(map);
    }
    extraOutput.put("targets", targets);
    extraOutput.put("securityGroupIngress", securityGroupIngress);
    operationContext.setOperations(list);
    operationContext.setExtraOutput(extraOutput);
    return operationContext;
  }

  @Override
  public boolean isSecurityGroupUpserted(SecurityGroup upsertedSecurityGroup, Stage stage) {
    if (upsertedSecurityGroup == null) {
      return false;
    }
    SecurityGroup securityGroup =
        mortService.getSecurityGroup(
            upsertedSecurityGroup.getAccountName(),
            cloudProvider,
            upsertedSecurityGroup.getName(),
            upsertedSecurityGroup.getRegion(),
            upsertedSecurityGroup.getVpcId());

    if (upsertedSecurityGroup.getName().equals(securityGroup.getName())) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public String getCloudProvider() {
    return cloudProvider;
  }
}
