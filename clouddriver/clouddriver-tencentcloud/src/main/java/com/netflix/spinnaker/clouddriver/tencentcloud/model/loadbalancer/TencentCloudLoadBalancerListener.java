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

package com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer;

import java.util.List;
import lombok.Data;

@Data
public class TencentCloudLoadBalancerListener {
  private String listenerId;
  private String listenerName;
  private String protocol;
  private Integer port;
  private TencentCloudLoadBalancerHealthCheck healthCheck;
  private TencentCloudLoadBalancerCertificate certificate;
  private Integer sessionExpireTime;
  private String scheduler;
  private Integer sniSwitch;
  // target, tcp/udp 4 layer
  private List<TencentCloudLoadBalancerTarget> targets;
  // rule, http/https 7 layer
  private List<TencentCloudLoadBalancerRule> rules;
}
