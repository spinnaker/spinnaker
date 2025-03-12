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

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.YandexDeployHandler;
import com.netflix.spinnaker.clouddriver.yandex.deploy.YandexServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.YandexInstanceGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

public class CloneYandexServerGroupAtomicOperation
    extends AbstractYandexAtomicOperation<YandexInstanceGroupDescription>
    implements AtomicOperation<DeploymentResult> {

  private static final String BASE_PHASE = "COPY_LAST_SERVER_GROUP";

  @Autowired private YandexDeployHandler deployHandler;

  public CloneYandexServerGroupAtomicOperation(YandexInstanceGroupDescription description) {
    super(description);
  }

  @Override
  public DeploymentResult operate(List<DeploymentResult> priorOutputs) {
    YandexInstanceGroupDescription newDescription = cloneAndOverrideDescription();

    YandexServerGroupNameResolver serverGroupNameResolver =
        new YandexServerGroupNameResolver(newDescription.getCredentials());
    String clusterName =
        serverGroupNameResolver.combineAppStackDetail(
            newDescription.getApplication(),
            newDescription.getStack(),
            newDescription.getFreeFormDetails());

    status(BASE_PHASE, "Initializing copy of server group for cluster '%s'...", clusterName);
    DeploymentResult result = deployHandler.handle(newDescription, priorOutputs);
    String newServerGroupName =
        single(result.getDeployments())
            .map(DeploymentResult.Deployment::getServerGroupName)
            .orElse(null);
    status(
        BASE_PHASE,
        "Finished copying server group for cluster '%s'. New server group '%s'.",
        clusterName,
        newServerGroupName);
    return result;
  }

  private YandexInstanceGroupDescription cloneAndOverrideDescription() {
    return Optional.ofNullable(description.getSource().getServerGroupName())
        .filter(name -> !name.isEmpty())
        .map(name -> getServerGroup(BASE_PHASE, name))
        .map(this::merge)
        .orElse(description);
  }

  // probably its easier to merge maps...
  private YandexInstanceGroupDescription merge(YandexCloudServerGroup ancestorServerGroup) {
    YandexInstanceGroupDescription.YandexInstanceGroupDescriptionBuilder newDescription =
        description.toBuilder();

    // Override any ancestor values that were specified directly on the cloneServerGroup call.
    Names ancestorNames = Names.parseName(ancestorServerGroup.getName());
    newDescription.application(firstNonEmpty(description.getApplication(), ancestorNames.getApp()));
    newDescription.stack(firstNonEmpty(description.getStack(), ancestorNames.getStack()));
    newDescription.freeFormDetails(
        firstNonEmpty(description.getFreeFormDetails(), ancestorNames.getDetail()));

    newDescription.description(
        firstNonEmpty(description.getDescription(), ancestorNames.getDetail()));
    newDescription.zones(firstNonEmpty(description.getZones(), ancestorServerGroup.getZones()));
    newDescription.labels(firstNonEmpty(description.getLabels(), ancestorServerGroup.getLabels()));
    newDescription.targetSize(
        firstNotNull(
            description.getTargetSize(),
            ancestorServerGroup.getCapacity().getDesired().longValue()));
    newDescription.autoScalePolicy(
        firstNotNull(description.getAutoScalePolicy(), ancestorServerGroup.getAutoScalePolicy()));
    newDescription.deployPolicy(
        firstNotNull(description.getDeployPolicy(), ancestorServerGroup.getDeployPolicy()));
    YandexCloudServerGroup.TargetGroupSpec ancestorTargetGroupSpec =
        ancestorServerGroup.getLoadBalancerIntegration() == null
            ? null
            : ancestorServerGroup.getLoadBalancerIntegration().getTargetGroupSpec();
    newDescription.targetGroupSpec(
        firstNotNull(description.getTargetGroupSpec(), ancestorTargetGroupSpec));
    newDescription.healthCheckSpecs(
        firstNonEmpty(
            description.getHealthCheckSpecs(), ancestorServerGroup.getHealthCheckSpecs()));
    newDescription.serviceAccountId(
        firstNonEmpty(
            description.getServiceAccountId(), ancestorServerGroup.getServiceAccountId()));

    YandexCloudServerGroup.InstanceTemplate template = description.getInstanceTemplate();
    if (template != null) {
      YandexCloudServerGroup.InstanceTemplate ancestorTemplate =
          ancestorServerGroup.getInstanceTemplate();
      YandexCloudServerGroup.InstanceTemplate.InstanceTemplateBuilder builder =
          template.toBuilder()
              .description(
                  firstNonEmpty(template.getDescription(), ancestorTemplate.getDescription()))
              .labels(firstNonEmpty(template.getLabels(), ancestorTemplate.getLabels()))
              .platformId(firstNonEmpty(template.getPlatformId(), ancestorTemplate.getPlatformId()))
              .resourcesSpec(
                  firstNotNull(template.getResourcesSpec(), ancestorTemplate.getResourcesSpec()))
              .metadata(firstNonEmpty(template.getMetadata(), ancestorTemplate.getMetadata()))
              .bootDiskSpec(
                  firstNotNull(template.getBootDiskSpec(), ancestorTemplate.getBootDiskSpec()))
              .secondaryDiskSpecs(
                  firstNonEmpty(
                      template.getSecondaryDiskSpecs(), ancestorTemplate.getSecondaryDiskSpecs()))
              .networkInterfaceSpecs(
                  firstNonEmpty(
                      template.getNetworkInterfaceSpecs(),
                      ancestorTemplate.getNetworkInterfaceSpecs()))
              .schedulingPolicy(
                  firstNotNull(
                      template.getSchedulingPolicy(), ancestorTemplate.getSchedulingPolicy()))
              .serviceAccountId(
                  firstNonEmpty(
                      template.getServiceAccountId(), ancestorTemplate.getServiceAccountId()));
      newDescription.instanceTemplate(builder.build());
    } else {
      newDescription.instanceTemplate(ancestorServerGroup.getInstanceTemplate());
    }

    newDescription.enableTraffic(
        description.getEnableTraffic() != null && description.getEnableTraffic());
    newDescription.balancers(ancestorServerGroup.getLoadBalancersWithHealthChecks());

    return newDescription.build();
  }

  private static <T> T firstNotNull(T first, T second) {
    return first == null ? second : first;
  }

  private static String firstNonEmpty(String first, String second) {
    return first == null || first.isEmpty() ? second : first;
  }

  private static <T, COLLECTION extends Collection<T>> COLLECTION firstNonEmpty(
      COLLECTION first, COLLECTION second) {
    return first == null || first.isEmpty() ? second : first;
  }

  private static <K, V> Map<K, V> firstNonEmpty(Map<K, V> first, Map<K, V> second) {
    return first == null || first.isEmpty() ? second : first;
  }
}
