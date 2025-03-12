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
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.DestroyYandexServerGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.List;
import java.util.Optional;

public class DestroyYandexServerGroupAtomicOperation
    extends AbstractYandexAtomicOperation<DestroyYandexServerGroupDescription>
    implements AtomicOperation<Void> {

  private static final String BASE_PHASE = YandexCloudFacade.DESTROY_SERVER_GROUP;

  public DestroyYandexServerGroupAtomicOperation(DestroyYandexServerGroupDescription description) {
    super(description);
  }

  @Override
  public Void operate(List<Void> priorOutputs) {
    String serverGroupName = description.getServerGroupName();
    status(BASE_PHASE, "Initializing destruction of server group '%s'...", serverGroupName);
    YandexCloudServerGroup serverGroup = getServerGroup(BASE_PHASE, serverGroupName);
    status(BASE_PHASE, "Checking for associated load balancers...");
    Optional.of(serverGroup)
        .map(YandexCloudServerGroup::getLoadBalancerIntegration)
        .ifPresent(this::detachFromLoadBalancers);
    yandexCloudFacade.deleteInstanceGroup(credentials, serverGroup.getId());
    status(BASE_PHASE, "Done destroying server group '%s'.", serverGroupName);
    return null;
  }

  public void detachFromLoadBalancers(YandexCloudServerGroup.LoadBalancerIntegration loadBalancer) {
    for (YandexCloudLoadBalancer balancer : loadBalancer.getBalancers()) {
      status(
          BASE_PHASE,
          "Detaching server group from associated load balancer '%s'...",
          balancer.getName());
      yandexCloudFacade.detachTargetGroup(
          BASE_PHASE, credentials, balancer, loadBalancer.getTargetGroupId());
      status(
          BASE_PHASE,
          "Detached server group from associated load balancer '%s'.",
          balancer.getName());
    }
    status(BASE_PHASE, "Detached server group from associated load balancers.");
  }
}
