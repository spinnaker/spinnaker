/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.deploy.ops;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.EnableDisableYandexServerGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import java.util.List;
import java.util.Optional;

public class DisableYandexServerGroupAtomicOperation
    extends AbstractYandexAtomicOperation<EnableDisableYandexServerGroupDescription>
    implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DISABLE_SERVER_GROUP";

  public DisableYandexServerGroupAtomicOperation(
      EnableDisableYandexServerGroupDescription description) {
    super(description);
  }

  @Override
  public Void operate(List<Void> priorOutputs) {
    String serverGroupName = description.getServerGroupName();
    status(BASE_PHASE, "Initializing disable server group operation for '%s'...", serverGroupName);
    YandexCloudServerGroup serverGroup = getServerGroup(BASE_PHASE, serverGroupName);
    status(BASE_PHASE, "Disabling server group '%s'...", serverGroupName);
    Optional.of(serverGroup)
        .map(YandexCloudServerGroup::getLoadBalancerIntegration)
        .ifPresent(this::disableInstanceGroup);
    status(BASE_PHASE, "Done disabling server group '%s'.", serverGroupName);
    return null;
  }

  private void disableInstanceGroup(YandexCloudServerGroup.LoadBalancerIntegration loadBalancer) {
    for (YandexCloudLoadBalancer balancer : loadBalancer.getBalancers()) {
      status(
          BASE_PHASE, "Deregistering server group from load balancer '%s'...", balancer.getName());
      yandexCloudFacade.detachTargetGroup(
          BASE_PHASE, credentials, balancer, loadBalancer.getTargetGroupId());
      status(
          BASE_PHASE,
          "Done deregistering server group from load balancer '%s'.",
          balancer.getName());
    }
  }
}
