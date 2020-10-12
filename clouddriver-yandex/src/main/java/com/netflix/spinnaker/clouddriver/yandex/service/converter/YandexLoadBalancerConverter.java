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

package com.netflix.spinnaker.clouddriver.yandex.service.converter;

import static yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerOuterClass.*;
import static yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerServiceOuterClass.*;

import com.google.common.base.Strings;
import com.google.protobuf.FieldMask;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.UpsertYandexLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudLoadBalancer;
import java.util.function.Consumer;

public class YandexLoadBalancerConverter {
  public static CreateNetworkLoadBalancerRequest mapToCreateRequest(
      UpsertYandexLoadBalancerDescription description) {
    CreateNetworkLoadBalancerRequest.Builder builder =
        CreateNetworkLoadBalancerRequest.newBuilder()
            .setFolderId(description.getCredentials().getFolder())
            .setRegionId(YandexCloudProvider.REGION)
            .setType(NetworkLoadBalancer.Type.valueOf(description.getLbType().name()));

    if (description.getName() != null) {
      builder.setName(description.getName());
    }
    if (description.getDescription() != null) {
      builder.setDescription(description.getDescription());
    }
    if (description.getLabels() != null) {
      builder.putAllLabels(description.getLabels());
    }
    if (description.getListeners() != null) {
      addListenerSpecs(description, builder::addListenerSpecs);
    }
    return builder.build();
  }

  private static void addListenerSpecs(
      UpsertYandexLoadBalancerDescription description, Consumer<ListenerSpec.Builder> builder) {
    description
        .getListeners()
        .forEach(listener -> builder.accept(getListenerBuilder(description.getLbType(), listener)));
  }

  private static ListenerSpec.Builder getListenerBuilder(
      YandexCloudLoadBalancer.BalancerType type, YandexCloudLoadBalancer.Listener listener) {
    ListenerSpec.Builder spec =
        ListenerSpec.newBuilder()
            .setName(listener.getName())
            .setPort(listener.getPort())
            .setTargetPort(listener.getTargetPort())
            .setProtocol(Listener.Protocol.valueOf(listener.getProtocol().name()));
    IpVersion ipVersion =
        listener.getIpVersion() == null
            ? IpVersion.IPV4
            : IpVersion.valueOf(listener.getIpVersion().name());
    if (type == YandexCloudLoadBalancer.BalancerType.INTERNAL) {
      InternalAddressSpec.Builder addressSpec =
          InternalAddressSpec.newBuilder()
              .setSubnetId(listener.getSubnetId())
              .setIpVersion(ipVersion);
      if (!Strings.isNullOrEmpty(listener.getAddress())) {
        addressSpec.setAddress(listener.getAddress());
      }
      spec.setInternalAddressSpec(addressSpec);
    } else {
      ExternalAddressSpec.Builder addressSpec =
          ExternalAddressSpec.newBuilder().setIpVersion(ipVersion);
      if (!Strings.isNullOrEmpty(listener.getAddress())) {
        addressSpec.setAddress(listener.getAddress());
      }
      spec.setExternalAddressSpec(addressSpec);
    }
    return spec;
  }

  public static UpdateNetworkLoadBalancerRequest mapToUpdateRequest(
      String networkLoadBalancerId, UpsertYandexLoadBalancerDescription description) {
    FieldMask.Builder updateMask = FieldMask.newBuilder();
    UpdateNetworkLoadBalancerRequest.Builder builder =
        UpdateNetworkLoadBalancerRequest.newBuilder()
            .setNetworkLoadBalancerId(networkLoadBalancerId);
    if (description.getName() != null) {
      updateMask.addPaths("name");
      builder.setName(description.getName());
    }
    if (description.getDescription() != null) {
      updateMask.addPaths("description");
      builder.setDescription(description.getDescription());
    }
    if (description.getLabels() != null) {
      updateMask.addPaths("labels");
      builder.putAllLabels(description.getLabels());
    }
    if (description.getListeners() != null) {
      updateMask.addPaths("listener_specs");
      addListenerSpecs(description, builder::addListenerSpecs);
    }
    return builder.setUpdateMask(updateMask).build();
  }
}
