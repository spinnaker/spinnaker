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

import static yandex.cloud.api.compute.v1.instancegroup.InstanceGroupOuterClass.InstanceGroup;
import static yandex.cloud.api.compute.v1.instancegroup.InstanceGroupServiceOuterClass.ListInstanceGroupsRequest;

import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.moniker.Namer;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.groovy.datetime.extensions.DateTimeExtensions;

public class YandexServerGroupNameResolver extends AbstractServerGroupNameResolver {
  private static final String PHASE = "YANDEX_DEPLOY";
  private final YandexCloudCredentials credentials;
  private final Namer<InstanceGroup> naming;

  public YandexServerGroupNameResolver(YandexCloudCredentials credentials) {
    this.credentials = credentials;
    this.naming =
        NamerRegistry.lookup()
            .withProvider(YandexCloudProvider.ID)
            .withAccount(credentials.getName())
            .withResource(InstanceGroup.class);
  }

  @Override
  public String combineAppStackDetail(String appName, String stack, String detail) {
    return super.combineAppStackDetail(appName, stack, detail);
  }

  @Override
  public String getPhase() {
    return PHASE;
  }

  @Override
  public String getRegion() {
    return YandexCloudProvider.REGION;
  }

  @Override
  public List<TakenSlot> getTakenSlots(String clusterName) {
    ListInstanceGroupsRequest request =
        ListInstanceGroupsRequest.newBuilder().setFolderId(credentials.getFolder()).build();
    return credentials.instanceGroupService().list(request).getInstanceGroupsList().stream()
        .map(
            group ->
                new TakenSlot(
                    group.getName(),
                    naming.deriveMoniker(group).getSequence(),
                    DateTimeExtensions.toDate(
                        Instant.ofEpochSecond(group.getCreatedAt().getSeconds()))))
        .collect(Collectors.toList());
  }
}
