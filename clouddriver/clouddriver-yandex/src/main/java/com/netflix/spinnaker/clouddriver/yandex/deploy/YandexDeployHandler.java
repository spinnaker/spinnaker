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

package com.netflix.spinnaker.clouddriver.yandex.deploy;

import static com.netflix.spinnaker.clouddriver.yandex.deploy.ops.AbstractYandexAtomicOperation.status;
import static yandex.cloud.api.compute.v1.instancegroup.InstanceGroupOuterClass.InstanceGroup;
import static yandex.cloud.api.compute.v1.instancegroup.InstanceGroupOuterClass.ScalePolicy;

import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.YandexInstanceGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Data
public class YandexDeployHandler implements DeployHandler<YandexInstanceGroupDescription> {
  private static final String BASE_PHASE = "DEPLOY";

  @Autowired private final YandexCloudFacade yandexCloudFacade;

  @Override
  public boolean handles(DeployDescription description) {
    return description instanceof YandexInstanceGroupDescription;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public DeploymentResult handle(YandexInstanceGroupDescription description, List priorOutputs) {
    YandexCloudCredentials credentials = description.getCredentials();
    status(
        BASE_PHASE,
        "Initializing creation of server group for application '%s' stack '%s'...",
        description.getApplication(),
        description.getStack());
    status(BASE_PHASE, "Looking up next sequence...");
    description.produceServerGroupName();
    status(BASE_PHASE, "Produced server group name '%s'.", description.getName());
    description.saturateLabels();
    status(BASE_PHASE, "Composing server group '%s'...", description.getName());
    InstanceGroup createdInstanceGroup =
        yandexCloudFacade.createInstanceGroup(BASE_PHASE, credentials, description);
    if (Boolean.TRUE.equals(description.getEnableTraffic()) && description.getBalancers() != null) {
      String targetGroupId = createdInstanceGroup.getLoadBalancerState().getTargetGroupId();
      yandexCloudFacade.enableInstanceGroup(
          BASE_PHASE, credentials, targetGroupId, description.getBalancers());
    }
    status(BASE_PHASE, "Done creating server group '%s'.", description.getName());
    return makeDeploymentResult(createdInstanceGroup, credentials);
  }

  @NotNull // todo: don't use InstanceGroup... will be fixed with cache agent refactoring
  private DeploymentResult makeDeploymentResult(
      InstanceGroup result, YandexCloudCredentials credentials) {
    DeploymentResult.Deployment deployment = new DeploymentResult.Deployment();
    deployment.setAccount(credentials.getName());
    DeploymentResult.Deployment.Capacity capacity = new DeploymentResult.Deployment.Capacity();
    if (result.getScalePolicy().hasAutoScale()) {
      ScalePolicy.AutoScale autoScale = result.getScalePolicy().getAutoScale();
      capacity.setMin(
          (int) (autoScale.getMinZoneSize() * result.getAllocationPolicy().getZonesCount()));
      capacity.setMax((int) autoScale.getMaxSize());
      capacity.setDesired((int) autoScale.getInitialSize());
    } else {
      int size = (int) result.getScalePolicy().getFixedScale().getSize();
      capacity.setMin(size);
      capacity.setMax(size);
      capacity.setDesired(size);
    }
    deployment.setCapacity(capacity);
    deployment.setCloudProvider(YandexCloudProvider.ID);
    String instanceGroupName = result.getName();
    deployment.setServerGroupName(instanceGroupName);

    DeploymentResult deploymentResult = new DeploymentResult();
    deploymentResult.setServerGroupNames(
        Collections.singletonList(YandexCloudProvider.REGION + ":" + instanceGroupName));
    deploymentResult.setServerGroupNameByRegion(
        Collections.singletonMap(YandexCloudProvider.REGION, instanceGroupName));
    deploymentResult.setDeployments(Collections.singleton(deployment));
    return deploymentResult;
  }
}
