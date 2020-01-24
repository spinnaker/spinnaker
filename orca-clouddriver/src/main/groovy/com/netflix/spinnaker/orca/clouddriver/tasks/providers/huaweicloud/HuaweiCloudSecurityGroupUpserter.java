/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.huaweicloud;

import com.netflix.spinnaker.orca.clouddriver.MortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup.SecurityGroupUpserter;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

@Component
public class HuaweiCloudSecurityGroupUpserter implements SecurityGroupUpserter, CloudProviderAware {

  @Override
  public String getCloudProvider() {
    return "huaweicloud";
  }

  @Autowired MortService mortService;

  @Override
  public SecurityGroupUpserter.OperationContext getOperationContext(Stage stage) {

    MortService.SecurityGroup securityGroup = new MortService.SecurityGroup();
    securityGroup.setName((String) stage.getContext().get("securityGroupName"));
    securityGroup.setRegion((String) stage.getContext().get("region"));
    securityGroup.setAccountName(getCredentials(stage));

    Map<String, Object> extraOutput =
        new HashMap() {
          {
            put(
                "targets",
                new ArrayList() {
                  {
                    add(securityGroup);
                  }
                });
          }
        };

    List ops =
        new ArrayList() {
          {
            add(
                new HashMap() {
                  {
                    put(SecurityGroupUpserter.OPERATION, stage.getContext());
                  }
                });
          }
        };

    SecurityGroupUpserter.OperationContext operationContext =
        new SecurityGroupUpserter.OperationContext();
    operationContext.setOperations(ops);
    operationContext.setExtraOutput(extraOutput);
    return operationContext;
  }

  @Override
  public boolean isSecurityGroupUpserted(
      MortService.SecurityGroup upsertedSecurityGroup, Stage stage) {
    if (upsertedSecurityGroup == null) {
      return false;
    }

    try {
      MortService.SecurityGroup securityGroup =
          mortService.getSecurityGroup(
              upsertedSecurityGroup.getAccountName(),
              getCloudProvider(),
              upsertedSecurityGroup.getName(),
              upsertedSecurityGroup.getRegion());

      return securityGroup != null;
    } catch (RetrofitError e) {
      if (404 != e.getResponse().getStatus()) {
        throw e;
      }
    }
    return false;
  }
}
