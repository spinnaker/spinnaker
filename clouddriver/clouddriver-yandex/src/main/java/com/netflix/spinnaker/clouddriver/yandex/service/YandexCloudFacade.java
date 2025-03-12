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

package com.netflix.spinnaker.clouddriver.yandex.service;

import static com.netflix.spinnaker.clouddriver.yandex.deploy.ops.AbstractYandexAtomicOperation.single;
import static com.netflix.spinnaker.clouddriver.yandex.deploy.ops.AbstractYandexAtomicOperation.status;

import com.google.common.base.Strings;
import com.google.protobuf.FieldMask;
import com.google.protobuf.InvalidProtocolBufferException;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.UpsertYandexLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.YandexInstanceGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudImage;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudInstance;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudNetwork;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServiceAccount;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudSubnet;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexLogRecord;
import com.netflix.spinnaker.clouddriver.yandex.model.health.YandexLoadBalancerHealth;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.clouddriver.yandex.service.converter.YandexInstanceGroupConverter;
import com.netflix.spinnaker.clouddriver.yandex.service.converter.YandexLoadBalancerConverter;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import yandex.cloud.api.compute.v1.ImageOuterClass;
import yandex.cloud.api.compute.v1.ImageServiceOuterClass;
import yandex.cloud.api.compute.v1.InstanceServiceOuterClass;
import yandex.cloud.api.compute.v1.instancegroup.InstanceGroupOuterClass;
import yandex.cloud.api.compute.v1.instancegroup.InstanceGroupServiceOuterClass;
import yandex.cloud.api.iam.v1.ServiceAccountServiceOuterClass;
import yandex.cloud.api.loadbalancer.v1.HealthCheckOuterClass;
import yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerOuterClass;
import yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerServiceOuterClass;
import yandex.cloud.api.operation.OperationOuterClass;
import yandex.cloud.api.vpc.v1.NetworkServiceOuterClass;
import yandex.cloud.api.vpc.v1.SubnetServiceOuterClass;

/**
 * Facade to Yandex API. Hides implementation details of Yandex.Cloud Java SDK
 *
 * <p>todo: - extract mappers into classes - better status and processing (class member or thread
 * local) - do not return yandex api object (always convert to spinnaker objects) - process lists in
 * a same way with pagination - probably substitute 'operationPoller.doSync' with completable future
 */
@Component
public class YandexCloudFacade {
  public static final String RESIZE_SERVER_GROUP = "RESIZE_SERVER_GROUP";
  public static final String REBOOT_INSTANCES = "REBOOT_INSTANCES";
  public static final String DELETE_LOAD_BALANCER = "DELETE_LOAD_BALANCER";
  public static final String UPSERT_IMAGE_TAGS = "UPSERT_IMAGE_TAGS";
  public static final String UPSERT_LOAD_BALANCER = "UPSERT_LOAD_BALANCER";
  public static final String MODIFY_INSTANCE_GROUP = "MODIFY_INSTANCE_GROUP";
  public static final String DESTROY_SERVER_GROUP = "DESTROY_SERVER_GROUP";

  @Autowired private YandexOperationPoller operationPoller;

  public List<YandexCloudImage> getImages(YandexCloudCredentials credentials, String folder) {
    return getImages(credentials, folder, null);
  }

  public YandexCloudImage getImage(YandexCloudCredentials credentials, String imageName) {
    return getImages(credentials, credentials.getFolder(), "name='" + imageName + "'").stream()
        .filter(i -> imageName.equals(i.getName()))
        .findFirst()
        .orElse(null);
  }

  private List<YandexCloudImage> getImages(
      YandexCloudCredentials credentials, String folder, String filter) {
    List<ImageOuterClass.Image> images = new ArrayList<>();
    String nextPageToken = "";
    ImageServiceOuterClass.ListImagesRequest.Builder builder =
        ImageServiceOuterClass.ListImagesRequest.newBuilder().setFolderId(folder);
    if (filter != null) {
      builder.setFilter(filter);
    }
    do {
      ImageServiceOuterClass.ListImagesRequest request =
          builder.setPageToken(nextPageToken).build();
      ImageServiceOuterClass.ListImagesResponse response = credentials.imageService().list(request);
      images.addAll(response.getImagesList());
      nextPageToken = response.getNextPageToken();
    } while (!Strings.isNullOrEmpty(nextPageToken));
    return images.stream().map(YandexCloudImage::createFromProto).collect(Collectors.toList());
  }

  public void updateImageTags(
      YandexCloudCredentials credentials, String imageId, Map<String, String> labels) {
    ImageServiceOuterClass.UpdateImageRequest request =
        ImageServiceOuterClass.UpdateImageRequest.newBuilder()
            .setImageId(imageId)
            .setUpdateMask(FieldMask.newBuilder().addPaths("labels").build())
            .putAllLabels(labels)
            .build();
    operationPoller.doSync(
        () -> credentials.imageService().update(request), credentials, UPSERT_IMAGE_TAGS);
  }

  public void createLoadBalancer(
      YandexCloudCredentials credentials, UpsertYandexLoadBalancerDescription description) {
    NetworkLoadBalancerServiceOuterClass.CreateNetworkLoadBalancerRequest request =
        YandexLoadBalancerConverter.mapToCreateRequest(description);
    operationPoller.doSync(
        () -> credentials.networkLoadBalancerService().create(request),
        credentials,
        UPSERT_LOAD_BALANCER);
  }

  public void updateLoadBalancer(
      String id,
      YandexCloudCredentials credentials,
      UpsertYandexLoadBalancerDescription description) {
    NetworkLoadBalancerServiceOuterClass.UpdateNetworkLoadBalancerRequest request =
        YandexLoadBalancerConverter.mapToUpdateRequest(id, description);
    operationPoller.doSync(
        () -> credentials.networkLoadBalancerService().update(request),
        credentials,
        UPSERT_LOAD_BALANCER);
  }

  public void deleteLoadBalancer(YandexCloudCredentials credentials, String id) {
    NetworkLoadBalancerServiceOuterClass.DeleteNetworkLoadBalancerRequest requests =
        NetworkLoadBalancerServiceOuterClass.DeleteNetworkLoadBalancerRequest.newBuilder()
            .setNetworkLoadBalancerId(id)
            .build();
    operationPoller.doSync(
        () -> credentials.networkLoadBalancerService().delete(requests),
        credentials,
        DELETE_LOAD_BALANCER);
  }

  public List<String> getLoadBalancerIds(YandexCloudCredentials credentials, String name) {
    NetworkLoadBalancerServiceOuterClass.ListNetworkLoadBalancersRequest listRequest =
        NetworkLoadBalancerServiceOuterClass.ListNetworkLoadBalancersRequest.newBuilder()
            .setFolderId(credentials.getFolder())
            .setFilter("name='" + name + "'")
            .build();
    NetworkLoadBalancerServiceOuterClass.ListNetworkLoadBalancersResponse response =
        credentials.networkLoadBalancerService().list(listRequest);
    return response.getNetworkLoadBalancersList().stream()
        .map(NetworkLoadBalancerOuterClass.NetworkLoadBalancer::getId)
        .collect(Collectors.toList());
  }

  public YandexCloudLoadBalancer getLoadBalancer(YandexCloudCredentials credentials, String name) {
    List<String> ids = getLoadBalancerIds(credentials, name);
    return single(ids)
        .map(id -> convertLoadBalancer(credentials, getLoadBalancer(null, credentials, id)))
        .orElse(null);
  }

  private NetworkLoadBalancerOuterClass.NetworkLoadBalancer getLoadBalancer(
      String phase, YandexCloudCredentials credentials, String id) {
    try {
      NetworkLoadBalancerServiceOuterClass.GetNetworkLoadBalancerRequest request =
          NetworkLoadBalancerServiceOuterClass.GetNetworkLoadBalancerRequest.newBuilder()
              .setNetworkLoadBalancerId(id)
              .build();
      return credentials.networkLoadBalancerService().get(request);
    } catch (StatusRuntimeException e) {
      throw new IllegalStateException(
          status(phase, "Could not resolve load balancer with id '%s'.", id));
    }
  }

  public List<YandexCloudLoadBalancer> getLoadBalancers(YandexCloudCredentials credentials) {
    NetworkLoadBalancerServiceOuterClass.ListNetworkLoadBalancersRequest build =
        NetworkLoadBalancerServiceOuterClass.ListNetworkLoadBalancersRequest.newBuilder()
            .setFolderId(credentials.getFolder())
            .build();
    NetworkLoadBalancerServiceOuterClass.ListNetworkLoadBalancersResponse response =
        credentials.networkLoadBalancerService().list(build);
    return response.getNetworkLoadBalancersList().stream()
        .map(balancer -> convertLoadBalancer(credentials, balancer))
        .collect(Collectors.toList());
  }

  public void resizeServerGroup(
      YandexCloudCredentials credentials, String serverGroupId, Integer capacity) {
    InstanceGroupServiceOuterClass.UpdateInstanceGroupRequest request =
        YandexInstanceGroupConverter.buildResizeRequest(serverGroupId, capacity);
    operationPoller.doSync(
        () -> credentials.instanceGroupService().update(request), credentials, RESIZE_SERVER_GROUP);
  }

  public void restrartInstance(YandexCloudCredentials credentials, String instanceId) {
    InstanceServiceOuterClass.RestartInstanceRequest request =
        InstanceServiceOuterClass.RestartInstanceRequest.newBuilder()
            .setInstanceId(instanceId)
            .build();
    operationPoller.doSync(
        () -> credentials.instanceService().restart(request), credentials, REBOOT_INSTANCES);
  }

  public List<YandexCloudInstance> getInstances(YandexCloudCredentials credentials) {
    InstanceServiceOuterClass.ListInstancesRequest request =
        InstanceServiceOuterClass.ListInstancesRequest.newBuilder()
            .setFolderId(credentials.getFolder())
            .build();
    return credentials.instanceService().list(request).getInstancesList().stream()
        .map(YandexCloudInstance::createFromProto)
        .collect(Collectors.toList());
  }

  public InstanceGroupOuterClass.InstanceGroup createInstanceGroup(
      String phase,
      YandexCloudCredentials credentials,
      YandexInstanceGroupDescription description) {
    InstanceGroupServiceOuterClass.CreateInstanceGroupRequest request =
        YandexInstanceGroupConverter.mapToCreateRequest(description);

    OperationOuterClass.Operation operation = credentials.instanceGroupService().create(request);
    operation = operationPoller.waitDone(credentials, operation, phase);
    try {
      return operation.getResponse().unpack(InstanceGroupOuterClass.InstanceGroup.class);
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Invalid protocol of creating instances group", e);
    }
  }

  public void updateInstanceGroup(
      YandexCloudCredentials credentials,
      String instanceGroupId,
      YandexInstanceGroupDescription description) {
    InstanceGroupServiceOuterClass.UpdateInstanceGroupRequest request =
        YandexInstanceGroupConverter.mapToUpdateRequest(description, instanceGroupId);
    operationPoller.doSync(
        () -> credentials.instanceGroupService().update(request),
        credentials,
        MODIFY_INSTANCE_GROUP);
  }

  public List<String> getServerGroupIds(
      YandexCloudCredentials credentials, String serverGroupName) {
    InstanceGroupServiceOuterClass.ListInstanceGroupsRequest listRequest =
        InstanceGroupServiceOuterClass.ListInstanceGroupsRequest.newBuilder()
            .setFolderId(credentials.getFolder())
            .setFilter("name='" + serverGroupName + "'")
            .setView(InstanceGroupServiceOuterClass.InstanceGroupView.FULL)
            .build();

    return credentials.instanceGroupService().list(listRequest).getInstanceGroupsList().stream()
        .map(InstanceGroupOuterClass.InstanceGroup::getId)
        .collect(Collectors.toList());
  }

  public void deleteInstanceGroup(YandexCloudCredentials credentials, String instanceGroupId) {
    InstanceGroupServiceOuterClass.DeleteInstanceGroupRequest request =
        InstanceGroupServiceOuterClass.DeleteInstanceGroupRequest.newBuilder()
            .setInstanceGroupId(instanceGroupId)
            .build();
    operationPoller.doSync(
        () -> credentials.instanceGroupService().delete(request),
        credentials,
        DESTROY_SERVER_GROUP);
  }

  public void enableInstanceGroup(
      String phase,
      YandexCloudCredentials credentials,
      String targetGroupId,
      Map<String, List<YandexCloudServerGroup.HealthCheckSpec>> loadBalancersSpecs) {
    status(phase, "Registering instances with network load balancers...");
    status(phase, "Retrieving load balancers...");
    // looks like the validation does't make any sense here...
    Map<String, String> balancers =
        loadBalancersSpecs.keySet().stream()
            .map(id -> getLoadBalancer(phase, credentials, id))
            .collect(
                Collectors.toMap(
                    NetworkLoadBalancerOuterClass.NetworkLoadBalancer::getId,
                    NetworkLoadBalancerOuterClass.NetworkLoadBalancer::getName));

    balancers.forEach(
        (id, name) -> {
          List<YandexCloudServerGroup.HealthCheckSpec> healthCheckSpecs =
              loadBalancersSpecs.get(id);
          status(phase, "Registering server group with load balancer '%s'...", name);
          attachTargetGroup(phase, credentials, id, targetGroupId, healthCheckSpecs);
          status(phase, "Done registering server group with load balancer '%s'.", name);
        });
  }

  public void detachTargetGroup(
      String phase,
      YandexCloudCredentials credentials,
      YandexCloudLoadBalancer balancer,
      String targetGroupId) {
    try {
      NetworkLoadBalancerServiceOuterClass.DetachNetworkLoadBalancerTargetGroupRequest request =
          NetworkLoadBalancerServiceOuterClass.DetachNetworkLoadBalancerTargetGroupRequest
              .newBuilder()
              .setNetworkLoadBalancerId(balancer.getId())
              .setTargetGroupId(targetGroupId)
              .build();
      operationPoller.doSync(
          () -> credentials.networkLoadBalancerService().detachTargetGroup(request),
          credentials,
          phase);
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode() != Status.Code.INVALID_ARGUMENT) {
        throw e;
      }
    }
  }

  public void attachTargetGroup(
      String phase,
      YandexCloudCredentials credentials,
      String id,
      String targetGroupId,
      List<YandexCloudServerGroup.HealthCheckSpec> healthCheckSpecs) {
    NetworkLoadBalancerOuterClass.AttachedTargetGroup.Builder targetGroup =
        NetworkLoadBalancerOuterClass.AttachedTargetGroup.newBuilder()
            .setTargetGroupId(targetGroupId);
    for (int idx = 0; idx < healthCheckSpecs.size(); idx++) {
      HealthCheckOuterClass.HealthCheck healthCheck =
          mapHealthCheckSpec(targetGroupId, idx, healthCheckSpecs.get(idx));
      targetGroup.addHealthChecks(healthCheck);
    }

    NetworkLoadBalancerServiceOuterClass.AttachNetworkLoadBalancerTargetGroupRequest request =
        NetworkLoadBalancerServiceOuterClass.AttachNetworkLoadBalancerTargetGroupRequest
            .newBuilder()
            .setNetworkLoadBalancerId(id)
            .setAttachedTargetGroup(targetGroup)
            .build();
    operationPoller.doSync(
        () -> credentials.networkLoadBalancerService().attachTargetGroup(request),
        credentials,
        phase);
  }

  private static HealthCheckOuterClass.HealthCheck mapHealthCheckSpec(
      String targetGroupId, int index, YandexCloudServerGroup.HealthCheckSpec hc) {
    HealthCheckOuterClass.HealthCheck.Builder builder =
        HealthCheckOuterClass.HealthCheck.newBuilder();
    if (hc.getType() == YandexCloudServerGroup.HealthCheckSpec.Type.HTTP) {
      builder.setHttpOptions(
          HealthCheckOuterClass.HealthCheck.HttpOptions.newBuilder()
              .setPort(hc.getPort())
              .setPath(hc.getPath()));
    } else {
      builder.setTcpOptions(
          HealthCheckOuterClass.HealthCheck.TcpOptions.newBuilder().setPort(hc.getPort()));
    }
    return builder
        .setName(targetGroupId + "-" + index)
        .setInterval(YandexInstanceGroupConverter.mapDuration(hc.getInterval()))
        .setTimeout(YandexInstanceGroupConverter.mapDuration(hc.getTimeout()))
        .setUnhealthyThreshold(hc.getUnhealthyThreshold())
        .setHealthyThreshold(hc.getHealthyThreshold())
        .build();
  }

  public List<YandexCloudNetwork> getNetworks(YandexCloudCredentials credentials) {
    NetworkServiceOuterClass.ListNetworksRequest request =
        NetworkServiceOuterClass.ListNetworksRequest.newBuilder()
            .setFolderId(credentials.getFolder())
            .build();
    return credentials.networkService().list(request).getNetworksList().stream()
        .map(network -> YandexCloudNetwork.createFromProto(network, credentials.getName()))
        .collect(Collectors.toList());
  }

  private YandexCloudLoadBalancer convertLoadBalancer(
      YandexCloudCredentials credentials,
      NetworkLoadBalancerOuterClass.NetworkLoadBalancer networkLoadBalancer) {
    Map<String, List<YandexLoadBalancerHealth>> healths =
        networkLoadBalancer.getAttachedTargetGroupsList().stream()
            .collect(
                Collectors.toMap(
                    NetworkLoadBalancerOuterClass.AttachedTargetGroup::getTargetGroupId,
                    tg -> getTargetStates(credentials, networkLoadBalancer, tg)));

    return YandexCloudLoadBalancer.createFromNetworkLoadBalancer(
        networkLoadBalancer, credentials.getName(), healths);
  }

  private List<YandexLoadBalancerHealth> getTargetStates(
      YandexCloudCredentials credentials,
      NetworkLoadBalancerOuterClass.NetworkLoadBalancer networkLoadBalancer,
      NetworkLoadBalancerOuterClass.AttachedTargetGroup tg) {
    NetworkLoadBalancerServiceOuterClass.GetTargetStatesRequest request =
        NetworkLoadBalancerServiceOuterClass.GetTargetStatesRequest.newBuilder()
            .setNetworkLoadBalancerId(networkLoadBalancer.getId())
            .setTargetGroupId(tg.getTargetGroupId())
            .build();
    List<NetworkLoadBalancerOuterClass.TargetState> targetStatesList =
        credentials.networkLoadBalancerService().getTargetStates(request).getTargetStatesList();
    return targetStatesList.stream()
        .map(
            state ->
                new YandexLoadBalancerHealth(
                    state.getAddress(),
                    state.getSubnetId(),
                    YandexLoadBalancerHealth.Status.valueOf(state.getStatus().name())))
        .collect(Collectors.toList());
  }

  public List<InstanceGroupOuterClass.InstanceGroup> getServerGroups(
      YandexCloudCredentials credentials) {
    InstanceGroupServiceOuterClass.ListInstanceGroupsRequest request =
        InstanceGroupServiceOuterClass.ListInstanceGroupsRequest.newBuilder()
            .setFolderId(credentials.getFolder())
            .setView(InstanceGroupServiceOuterClass.InstanceGroupView.FULL)
            .build();
    return credentials.instanceGroupService().list(request).getInstanceGroupsList();
  }

  public Optional<InstanceGroupOuterClass.InstanceGroup> getServerGroup(
      YandexCloudCredentials credentials, String name) {
    try {
      InstanceGroupServiceOuterClass.ListInstanceGroupsResponse response =
          credentials
              .instanceGroupService()
              .list(
                  InstanceGroupServiceOuterClass.ListInstanceGroupsRequest.newBuilder()
                      .setFolderId(credentials.getFolder())
                      .setFilter("name='" + name + "'")
                      .setView(InstanceGroupServiceOuterClass.InstanceGroupView.FULL)
                      .build());
      List<InstanceGroupOuterClass.InstanceGroup> instanceGroupsList =
          response.getInstanceGroupsList();
      if (instanceGroupsList.size() != 1) {
        return Optional.empty();
      }
      return response.getInstanceGroupsList().stream().findAny();
    } catch (StatusRuntimeException ignored) {
      return Optional.empty();
    }
  }

  public List<YandexCloudSubnet> getSubnets(YandexCloudCredentials credentials, String folderId) {
    SubnetServiceOuterClass.ListSubnetsRequest request =
        SubnetServiceOuterClass.ListSubnetsRequest.newBuilder().setFolderId(folderId).build();
    return credentials.subnetService().list(request).getSubnetsList().stream()
        .map(subnet -> YandexCloudSubnet.createFromProto(subnet, credentials.getName()))
        .collect(Collectors.toList());
  }

  public List<YandexCloudServiceAccount> getServiceAccounts(
      YandexCloudCredentials credentials, String folder) {
    ServiceAccountServiceOuterClass.ListServiceAccountsRequest request =
        ServiceAccountServiceOuterClass.ListServiceAccountsRequest.newBuilder()
            .setFolderId(folder)
            .build();
    return credentials.serviceAccountService().list(request).getServiceAccountsList().stream()
        .map(sa -> YandexCloudServiceAccount.createFromProto(sa, credentials.getName()))
        .collect(Collectors.toList());
  }

  public List<YandexLogRecord> getLogRecords(
      YandexCloudCredentials credentials, String serverGroupId) {
    InstanceGroupServiceOuterClass.ListInstanceGroupLogRecordsRequest request =
        InstanceGroupServiceOuterClass.ListInstanceGroupLogRecordsRequest.newBuilder()
            .setInstanceGroupId(serverGroupId)
            .build();
    return credentials.instanceGroupService().listLogRecords(request).getLogRecordsList().stream()
        .map(YandexLogRecord::createFromProto)
        .collect(Collectors.toList());
  }

  public String getSerialPortOutput(YandexCloudCredentials credentials, String instanceId) {
    InstanceServiceOuterClass.GetInstanceSerialPortOutputRequest request =
        InstanceServiceOuterClass.GetInstanceSerialPortOutputRequest.newBuilder()
            .setInstanceId(instanceId)
            .build();
    return credentials.instanceService().getSerialPortOutput(request).getContents();
  }

  public Set<String> getServerGroupInstanceIds(
      YandexCloudCredentials credentials, String serverGroupId) {
    try {
      InstanceGroupServiceOuterClass.ListInstanceGroupInstancesRequest request =
          InstanceGroupServiceOuterClass.ListInstanceGroupInstancesRequest.newBuilder()
              .setInstanceGroupId(serverGroupId)
              .build();
      return credentials.instanceGroupService().listInstances(request).getInstancesList().stream()
          .map(InstanceGroupOuterClass.ManagedInstance::getInstanceId)
          .collect(Collectors.toSet());
    } catch (StatusRuntimeException ex) {
      if (ex.getStatus() == io.grpc.Status.NOT_FOUND) {
        return Collections.emptySet();
      } else {
        throw ex;
      }
    }
  }
}
