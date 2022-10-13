/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.cloudrun;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.model.Instance;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.AbstractInstancesCheckTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitingForInstancesTaskHelper;
import groovy.util.logging.Slf4j;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WaitForCloudrunInstancesDownTask extends AbstractInstancesCheckTask {

  @Override
  protected Map<String, List<String>> getServerGroups(StageExecution stage) {
    return WaitingForInstancesTaskHelper.extractServerGroups(stage);
  }

  @Override
  protected boolean hasSucceeded(
      StageExecution stage,
      ServerGroup serverGroup,
      List<Instance> instances,
      Collection<String> interestingHealthProviderNames) {
    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      return true;
    }
    String cloudProvider = getCloudProvider(stage);
    if ("cloudrun".equals(cloudProvider)) {
      return true;
    }
    return false;
  }
}
