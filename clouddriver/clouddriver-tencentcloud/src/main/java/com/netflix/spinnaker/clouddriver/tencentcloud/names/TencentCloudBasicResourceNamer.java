/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.clouddriver.tencentcloud.names;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.names.NamingStrategy;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudBasicResource;
import com.netflix.spinnaker.moniker.Moniker;

public class TencentCloudBasicResourceNamer implements NamingStrategy<TencentCloudBasicResource> {

  @Override
  public String getName() {
    return "tencentCloudAnnotations";
  }

  public void applyMoniker(TencentCloudBasicResource tencentCloudBasicResource, Moniker moniker) {}

  @Override
  public Moniker deriveMoniker(TencentCloudBasicResource tencentCloudBasicResource) {
    String name = tencentCloudBasicResource.getMonikerName();
    Names parsed = Names.parseName(name);

    return Moniker.builder()
        .app(parsed.getApp())
        .cluster(parsed.getCluster())
        .detail(parsed.getDetail())
        .stack(parsed.getStack())
        .detail(parsed.getDetail())
        .sequence(parsed.getSequence())
        .build();
  }
}
