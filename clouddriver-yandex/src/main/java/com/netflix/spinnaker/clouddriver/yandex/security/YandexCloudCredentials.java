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
package com.netflix.spinnaker.clouddriver.yandex.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import io.grpc.Channel;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import lombok.Data;
import yandex.cloud.api.compute.v1.ImageServiceGrpc;
import yandex.cloud.api.compute.v1.InstanceServiceGrpc;
import yandex.cloud.api.compute.v1.SnapshotServiceGrpc;
import yandex.cloud.api.compute.v1.instancegroup.InstanceGroupServiceGrpc;
import yandex.cloud.api.iam.v1.ServiceAccountServiceGrpc;
import yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerServiceGrpc;
import yandex.cloud.api.operation.OperationServiceGrpc;
import yandex.cloud.api.vpc.v1.NetworkServiceGrpc;
import yandex.cloud.api.vpc.v1.SubnetServiceGrpc;
import yandex.cloud.sdk.ServiceFactory;

@Data
public class YandexCloudCredentials
    implements AccountCredentials<YandexCloudCredentials.YandexCredentials> {
  private String name;
  private String environment;
  private String accountType;

  private String folder;

  private ServiceFactory serviceFactory;

  @Override
  @JsonIgnore
  public YandexCredentials getCredentials() {
    return null;
  }

  @Override
  public String getCloudProvider() {
    return YandexCloudProvider.ID;
  }

  @Override
  public List<String> getRequiredGroupMembership() {
    return Collections.emptyList();
  }

  public OperationServiceGrpc.OperationServiceBlockingStub operationService() {
    return create(
        OperationServiceGrpc.OperationServiceBlockingStub.class,
        OperationServiceGrpc::newBlockingStub);
  }

  public InstanceServiceGrpc.InstanceServiceBlockingStub instanceService() {
    return create(
        InstanceServiceGrpc.InstanceServiceBlockingStub.class,
        InstanceServiceGrpc::newBlockingStub);
  }

  public InstanceGroupServiceGrpc.InstanceGroupServiceBlockingStub instanceGroupService() {
    return create(
        InstanceGroupServiceGrpc.InstanceGroupServiceBlockingStub.class,
        InstanceGroupServiceGrpc::newBlockingStub);
  }

  public ImageServiceGrpc.ImageServiceBlockingStub imageService() {
    return create(
        ImageServiceGrpc.ImageServiceBlockingStub.class, ImageServiceGrpc::newBlockingStub);
  }

  public SnapshotServiceGrpc.SnapshotServiceBlockingStub snapshotService() {
    return create(
        SnapshotServiceGrpc.SnapshotServiceBlockingStub.class,
        SnapshotServiceGrpc::newBlockingStub);
  }

  public NetworkServiceGrpc.NetworkServiceBlockingStub networkService() {
    return create(
        NetworkServiceGrpc.NetworkServiceBlockingStub.class, NetworkServiceGrpc::newBlockingStub);
  }

  public SubnetServiceGrpc.SubnetServiceBlockingStub subnetService() {
    return create(
        SubnetServiceGrpc.SubnetServiceBlockingStub.class, SubnetServiceGrpc::newBlockingStub);
  }

  public NetworkLoadBalancerServiceGrpc.NetworkLoadBalancerServiceBlockingStub
      networkLoadBalancerService() {
    return create(
        NetworkLoadBalancerServiceGrpc.NetworkLoadBalancerServiceBlockingStub.class,
        NetworkLoadBalancerServiceGrpc::newBlockingStub);
  }

  public ServiceAccountServiceGrpc.ServiceAccountServiceBlockingStub serviceAccountService() {
    return create(
        ServiceAccountServiceGrpc.ServiceAccountServiceBlockingStub.class,
        ServiceAccountServiceGrpc::newBlockingStub);
  }

  private <SERVICE extends io.grpc.stub.AbstractStub<SERVICE>> SERVICE create(
      Class<SERVICE> clazz, Function<Channel, SERVICE> service) {
    return serviceFactory.create(clazz, service);
  }

  public static class YandexCredentials {}
}
