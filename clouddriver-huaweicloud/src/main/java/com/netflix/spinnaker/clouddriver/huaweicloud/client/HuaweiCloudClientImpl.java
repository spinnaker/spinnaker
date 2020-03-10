/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.huaweicloud.client;

import com.huawei.openstack4j.api.OSClient;
import com.huawei.openstack4j.model.compute.ext.AvailabilityZone;
import com.huawei.openstack4j.openstack.ecs.v1.domain.Flavor;
import com.huawei.openstack4j.openstack.ims.v2.domain.Image;
import com.huawei.openstack4j.openstack.vpc.v1.domain.SecurityGroup;
import com.huawei.openstack4j.openstack.vpc.v1.domain.Subnet;
import com.huawei.openstack4j.openstack.vpc.v1.domain.Vpc;
import com.netflix.spinnaker.clouddriver.huaweicloud.exception.HuaweiCloudException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

public class HuaweiCloudClientImpl implements HuaweiCloudClient {
  private final AuthorizedClientProvider provider;

  public HuaweiCloudClientImpl(AuthorizedClientProvider provider) {
    this.provider = provider;
  }

  private static <T> T handleInvoking(String doWhat, Callable<T> closure, T defaultResult) {
    try {
      T r = closure.call();
      return r == null ? defaultResult : r;
    } catch (Exception e) {
      throw new HuaweiCloudException(doWhat, e);
    }
  }

  private static List emptyList() {
    return Collections.emptyList();
  }

  private OSClient getRegionClient(String region) {
    return this.provider.getAuthClient().useRegion(region);
  }

  @Override
  public List<? extends AvailabilityZone> getZones(String region) throws HuaweiCloudException {
    return handleInvoking(
        String.format("getting zones in region(%s)", region),
        () -> getRegionClient(region).compute().zones().list(),
        emptyList());
  }

  @Override
  public List<? extends Image> getImages(String region) throws HuaweiCloudException {
    return handleInvoking(
        String.format("getting images in region(%s)", region),
        () ->
            getRegionClient(region)
                .imsV2()
                .images()
                .list(
                    new HashMap() {
                      {
                        put("__imagetype", "gold");
                        put("status", "active");
                        put("virtual_env_type", "FusionCompute");
                      }
                    }),
        emptyList());
  }

  @Override
  public List<? extends Flavor> getInstanceTypes(String region, String az)
      throws HuaweiCloudException {
    return handleInvoking(
        String.format("getting flavors in availability zone(%s) of region(%s)", az, region),
        () -> getRegionClient(region).ecs().servers().getSpecifications(az),
        emptyList());
  }

  @Override
  public List<? extends SecurityGroup> getSecurityGroups(String region)
      throws HuaweiCloudException {
    return handleInvoking(
        String.format("getting all security groups in region(%s)", region),
        () -> getRegionClient(region).vpc().securityGroups().list(),
        emptyList());
  }

  @Override
  public List<? extends Subnet> getSubnets(String region) throws HuaweiCloudException {
    return handleInvoking(
        String.format("getting all subnets in region(%s)", region),
        () -> getRegionClient(region).vpc().subnets().list(),
        emptyList());
  }

  @Override
  public List<? extends Vpc> getVpcs(String region) throws HuaweiCloudException {
    return handleInvoking(
        String.format("getting all vpcs in region(%s)", region),
        () -> getRegionClient(region).vpc().vpcs().list(),
        emptyList());
  }
}
