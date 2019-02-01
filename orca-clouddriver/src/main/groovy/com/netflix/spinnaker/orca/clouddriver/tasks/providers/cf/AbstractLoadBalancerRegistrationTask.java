/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.AbstractInstanceLoadBalancerRegistrationTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

abstract class AbstractLoadBalancerRegistrationTask extends AbstractInstanceLoadBalancerRegistrationTask implements Task {
  @Autowired
  TargetServerGroupResolver tsgResolver;

  @Override
  @Nonnull
  public TaskResult execute(@Nonnull Stage stage) {
    List<TargetServerGroup> tsgList = tsgResolver.resolve(stage);
    if (!tsgList.isEmpty()) {
      Optional.ofNullable(tsgList.get(0)).ifPresent(tsg -> stage.getContext().put("serverGroupName", tsg.getName()));
    }

    return super.execute(stage);
  }
}
